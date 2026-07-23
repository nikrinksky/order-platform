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

@Slf4j
@Service
@RequiredArgsConstructor
public class UserConsumerService {

    private final UserRepository userRepository;

    @KafkaListener(topics = "user.created", groupId = "user-service-group", autoStartup = "true")
    public void consumeUserCreated(@Payload UserCreatedEvent event) {
        try {
            log.info("Received user.created event: {}", event.getEmail());

            if (userRepository.existsByEmail(event.getEmail())) {
                log.warn("User already exists: {}", event.getEmail());
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
        } catch (Exception e) {
            log.error("Error processing user.created event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "user.updated", groupId = "user-service-group", autoStartup = "true")
    public void consumeUserUpdated(@Payload UserUpdatedEvent event) {
        try {
            log.info("Received user.updated event: {}", event.getEmail());

            Optional<User> existingUser = userRepository.findById(event.getId());
            if (existingUser.isPresent()) {
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
            } else {
                log.warn("User not found for update: {}", event.getId());
            }
        } catch (Exception e) {
            log.error("Error processing user.updated event: {}", e.getMessage(), e);
        }
    }
}
