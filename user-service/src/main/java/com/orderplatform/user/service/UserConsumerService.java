package com.orderplatform.user.service;

import com.orderplatform.events.user.UserCreatedEvent;
import com.orderplatform.events.user.UserUpdatedEvent;
import com.orderplatform.user.model.Role;
import com.orderplatform.user.model.User;
import com.orderplatform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

        if (userRepository.existsByEmail(event.getEmail().toString())) {
            log.warn("User already exists, skipping: {}", event.getEmail());
            return;
        }

        User user = User.builder()
                .id(event.getId().toString())
                .username(event.getUsername().toString())
                .email(event.getEmail().toString())
                .firstName(asString(event.getFirstName()))
                .lastName(asString(event.getLastName()))
                .roles(event.getRoles().stream()
                        .map(role -> Role.valueOf(role.toString()))
                        .collect(Collectors.toSet()))
                .isActive(event.getIsActive())
                .createdAt(toLocalDateTime(event.getCreatedAt()))
                .build();

        userRepository.save(user);
        log.info("User saved successfully: {}", user.getEmail());
    }

    @KafkaListener(topics = "user.updated", groupId = "user-service-group", autoStartup = "true")
    public void consumeUserUpdated(@Payload UserUpdatedEvent event) {
        log.info("Received user.updated event: {}", event.getEmail());

        Optional<User> existingUser = userRepository.findById(event.getId().toString());
        if (existingUser.isEmpty()) {
            log.warn("User not found for update: {}", event.getId());
            return;
        }

        User user = existingUser.get();

        User updatedUser = User.builder()
                .id(user.getId())
                .username(event.getUsername().toString())
                .email(user.getEmail())
                .firstName(asString(event.getFirstName()))
                .lastName(asString(event.getLastName()))
                .roles(event.getRoles().stream()
                        .map(role -> Role.valueOf(role.toString()))
                        .collect(Collectors.toSet()))
                .isActive(event.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(toLocalDateTime(event.getUpdatedAt()))
                .build();

        userRepository.save(updatedUser);
        log.info("User updated successfully: {}", updatedUser.getEmail());
    }

    /**
     * Avro string fields are {@link CharSequence}, the entity holds {@link String}.
     */
    private static String asString(CharSequence value) {
        return value == null ? null : value.toString();
    }

    /**
     * Avro timestamps arrive as {@link Instant}, the entity stores {@link LocalDateTime}.
     */
    private static LocalDateTime toLocalDateTime(Instant timestamp) {
        return timestamp == null ? null : LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC);
    }
}
