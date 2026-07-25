/**
 * Service for user operations.
 * Handles user registration, updates and Kafka event publishing.
 */
package com.orderplatform.auth.service;

import com.orderplatform.auth.dto.RegisterRequest;
import com.orderplatform.auth.dto.UserCreatedEvent;
import com.orderplatform.auth.dto.UserDto;
import com.orderplatform.auth.dto.UserUpdatedEvent;
import com.orderplatform.auth.model.Role;
import com.orderplatform.auth.model.User;
import com.orderplatform.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.kafka.common.requests.DeleteAclsResponse.log;

/**
 * Service for user operations.
 * Handles user registration, updates and Kafka event publishing.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Registers a new user.
     *
     * @param request the registration request containing user details
     * @return the registered user DTO
     */
    public UserDto register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            String msg = "User with email " + request.getEmail() + " already exists";
            throw new RuntimeException(msg);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .roles(Set.of(Role.ROLE_USER))
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);

        sendUserCreatedEvent(savedUser);

        return userMapper.toDto(savedUser);
    }

    /**
     * Updates an existing user.
     *
     * @param userId the user ID to update
     * @param request the update request containing user details
     * @return the updated user DTO
     */
    public UserDto updateUser(String userId, RegisterRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        User updatedUser = userRepository.save(user);

        sendUserUpdatedEvent(updatedUser);

        return userMapper.toDto(updatedUser);
    }

    /**
     * Sends a user created event to Kafka.
     *
     * @param user the saved user entity
     */
    private void sendUserCreatedEvent(User user) {
        if (kafkaTemplate != null) {
            try {
                UserCreatedEvent event = UserCreatedEvent.builder()
                        .id(user.getId())
                        .username(user.getEmail())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .roles(user.getRoles().stream()
                                .map(Enum::name)
                                .collect(Collectors.toSet()))
                        .isActive(user.isActive())
                        .createdAt(user.getCreatedAt())
                        .build();

                kafkaTemplate.send("user.created", user.getId(), event);
                log.info("UserCreatedEvent sent for user: {}", user.getEmail());
            } catch (Exception e) {
                log.error("Failed to send UserCreatedEvent: {}", e.getMessage());
            }
        } else {
            log.warn("KafkaTemplate is not available - skipping event send");
        }
    }

    /**
     * Sends a user updated event to Kafka.
     *
     * @param user the updated user entity
     */
    private void sendUserUpdatedEvent(User user) {
        if (kafkaTemplate != null) {
            try {
                UserUpdatedEvent event = UserUpdatedEvent.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .roles(user.getRoles().stream()
                                .map(Enum::name)
                                .collect(Collectors.toSet()))
                        .isActive(user.isActive())
                        .updatedAt(user.getUpdatedAt())
                        .build();

                kafkaTemplate.send("user.updated", user.getId(), event);
                log.info("UserUpdatedEvent sent for user: {}", user.getEmail());
            } catch (Exception e) {
                log.error("Failed to send UserUpdatedEvent: {}", e.getMessage());
            }
        } else {
            log.warn("KafkaTemplate is not available - skipping event send");
        }
    }
}
