package com.orderplatform.user.service;

import com.orderplatform.user.dto.UserCreatedEvent;
import com.orderplatform.user.model.Role;
import com.orderplatform.user.model.User;
import com.orderplatform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

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
}