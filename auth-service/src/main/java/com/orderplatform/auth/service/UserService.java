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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Service for user operations.
 * Handles user registration, updates and Kafka event publishing.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    
    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

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
     * Replaces the roles of a user.
     *
     * <p>Registration always assigns {@code ROLE_USER}, so this is the only way to promote an
     * account to {@code ROLE_MANAGER} or {@code ROLE_ADMIN} without touching the database.
     *
     * @param userId the user ID whose roles are replaced
     * @param roles the new, non-empty set of roles
     * @return the updated user DTO
     */
    public UserDto updateRoles(String userId, Set<Role> roles) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        user.setRoles(roles);

        User updatedUser = userRepository.save(user);

        sendUserUpdatedEvent(updatedUser);

        log.info("Roles updated for user {}: {}", updatedUser.getEmail(), roles);

        return userMapper.toDto(updatedUser);
    }

    /**
     * Ensures an administrator account exists, creating it on first startup.
     *
     * <p>Idempotent by design: an account that already carries {@code ROLE_ADMIN} is left
     * untouched, and an account that exists without the role gets it granted - its password is
     * never overwritten, so the bootstrap cannot lock out a real user sharing the address.
     *
     * @param email administrator email
     * @param password administrator password, used only when the account is created
     * @param firstName administrator first name
     * @param lastName administrator last name
     */
    public void ensureAdmin(String email, String password, String firstName, String lastName) {
        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isPresent()) {
            User admin = existing.get();
            if (admin.getRoles().contains(Role.ROLE_ADMIN)) {
                log.info("Admin account already present, bootstrap is a no-op: {}", email);
                return;
            }

            Set<Role> roles = new HashSet<>(admin.getRoles());
            roles.add(Role.ROLE_ADMIN);
            admin.setRoles(roles);

            sendUserUpdatedEvent(userRepository.save(admin));
            log.info("Granted ROLE_ADMIN to existing account: {}", email);
            return;
        }

        User admin = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .firstName(firstName)
                .lastName(lastName)
                .roles(Set.of(Role.ROLE_ADMIN))
                .isActive(true)
                .build();

        sendUserCreatedEvent(userRepository.save(admin));
        log.info("Admin account created: {}", email);
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
            log.warn("KafkaTemplate is not available - skipping UserCreatedEvent send");
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
            log.warn("KafkaTemplate is not available - skipping UserUpdatedEvent send");
        }
    }
}
