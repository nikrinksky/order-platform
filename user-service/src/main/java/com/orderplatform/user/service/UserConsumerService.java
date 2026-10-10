package com.orderplatform.user.service;

import com.orderplatform.user.dto.UserCreatedEvent;
import com.orderplatform.user.dto.UserUpdatedEvent;
import com.orderplatform.user.model.Role;
import com.orderplatform.user.model.User;
import com.orderplatform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Projects the auth-service user events into the read model of user-service.
 *
 * <p>Failures are deliberately <em>not</em> swallowed: an exception propagates to the Kafka
 * listener container, which retries the record and eventually routes it to the dead-letter
 * handling. Catching and logging the error here used to mark the offset as processed, so a user
 * that failed to save was silently lost and {@code /api/users} stayed empty.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserConsumerService {

    private final UserRepository userRepository;

    @KafkaListener(topics = "user.created", groupId = "user-service-group", autoStartup = "true")
    public void consumeUserCreated(@Payload UserCreatedEvent event) {
        log.info("Received user.created event: {}", event.getEmail());

        if (userRepository.existsByEmail(event.getEmail())) {
            log.warn("User already exists, skipping: {}", event.getEmail());
            return;
        }

        User user = User.builder()
                .id(event.getId())
                .username(event.getUsername())
                .email(event.getEmail())
                .firstName(event.getFirstName())
                .lastName(event.getLastName())
                .roles(event.getRoles().stream()
                        .map(Role::valueOf)
                        .collect(Collectors.toSet()))
                .isActive(event.isActive())
                .createdAt(event.getCreatedAt())
                .build();

        userRepository.save(user);
        log.info("User saved successfully: {}", user.getEmail());
    }

    @KafkaListener(topics = "user.updated", groupId = "user-service-group", autoStartup = "true")
    public void consumeUserUpdated(@Payload UserUpdatedEvent event) {
        log.info("Received user.updated event: {}", event.getEmail());

        Optional<User> existingUser = userRepository.findById(event.getId());
        if (existingUser.isEmpty()) {
            log.warn("User not found for update: {}", event.getId());
            return;
        }

        User user = existingUser.get();

        User updatedUser = User.builder()
                .id(user.getId())
                .username(event.getUsername())
                .email(user.getEmail())
                .firstName(event.getFirstName())
                .lastName(event.getLastName())
                .roles(event.getRoles().stream()
                        .map(Role::valueOf)
                        .collect(Collectors.toSet()))
                .isActive(event.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();

        userRepository.save(updatedUser);
        log.info("User updated successfully: {}", updatedUser.getEmail());
    }
}
