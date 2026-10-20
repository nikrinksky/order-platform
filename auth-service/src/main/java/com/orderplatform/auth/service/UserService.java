/**
 * Service for user operations.
 * Handles user registration, updates and event publication through the outbox.
 */
package com.orderplatform.auth.service;

import com.orderplatform.auth.dto.RegisterRequest;
import com.orderplatform.auth.dto.UserDto;
import com.orderplatform.auth.exception.UserAlreadyExistsException;
import com.orderplatform.auth.exception.UserNotFoundException;
import com.orderplatform.auth.model.Role;
import com.orderplatform.auth.model.User;
import com.orderplatform.auth.repository.UserRepository;
import com.orderplatform.events.user.UserCreatedEvent;
import com.orderplatform.events.user.UserUpdatedEvent;
import com.orderplatform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Service for user operations.
 * Handles user registration, updates and event publication through the outbox.
 *
 * <p>Events go through the transactional outbox rather than straight to Kafka: the account and the
 * event that announces it are written in one transaction, so a broker outage can no longer leave a
 * registered user that user-service never learns about.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final OutboxService outboxService;

    /**
     * Registers a new user.
     *
     * @param request the registration request containing user details
     * @return the registered user DTO
     */
    @Transactional
    public UserDto register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            String msg = "User with email " + request.getEmail() + " already exists";
            throw new UserAlreadyExistsException(msg);
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

        appendUserCreated(savedUser);

        return userMapper.toDto(savedUser);
    }

    /**
     * Updates an existing user.
     *
     * @param userId the user ID to update
     * @param request the update request containing user details
     * @return the updated user DTO
     */
    @Transactional
    public UserDto updateUser(String userId, RegisterRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        User updatedUser = userRepository.save(user);

        appendUserUpdated(updatedUser);

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
    @Transactional
    public UserDto updateRoles(String userId, Set<Role> roles) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        user.setRoles(roles);

        User updatedUser = userRepository.save(user);

        appendUserUpdated(updatedUser);

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
    @Transactional
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

            appendUserUpdated(userRepository.save(admin));
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

        appendUserCreated(userRepository.save(admin));
        log.info("Admin account created: {}", email);
    }

    /**
     * Appends the user.created event inside the caller's transaction.
     *
     * <p>The event id is derived from the business operation, so a retried registration cannot
     * append the same announcement twice.
     *
     * @param user the saved user entity
     */
    private void appendUserCreated(User user) {
        outboxService.append(Topics.USER_CREATED, user.getId(), user.getId() + ":CREATED",
                UserCreatedEvent.newBuilder()
                        .setId(user.getId())
                        .setUsername(user.getEmail())
                        .setEmail(user.getEmail())
                        .setFirstName(user.getFirstName())
                        .setLastName(user.getLastName())
                        .setRoles(roleNames(user))
                        .setIsActive(user.isActive())
                        .setCreatedAt(toInstant(user.getCreatedAt()))
                        .build());
        log.info("UserCreatedEvent appended for user: {}", user.getEmail());
    }

    /**
     * Appends the user.updated event inside the caller's transaction.
     *
     * @param user the updated user entity
     */
    private void appendUserUpdated(User user) {
        outboxService.append(Topics.USER_UPDATED, user.getId(), user.getId() + ":UPDATED",
                UserUpdatedEvent.newBuilder()
                        .setId(user.getId())
                        .setUsername(user.getEmail())
                        .setEmail(user.getEmail())
                        .setFirstName(user.getFirstName())
                        .setLastName(user.getLastName())
                        .setRoles(roleNames(user))
                        .setIsActive(user.isActive())
                        .setUpdatedAt(toInstant(user.getUpdatedAt()))
                        .build());
        log.info("UserUpdatedEvent appended for user: {}", user.getEmail());
    }

    private static List<CharSequence> roleNames(User user) {
        return new ArrayList<>(user.getRoles().stream().map(Enum::name).toList());
    }

    /**
     * The entity keeps timestamps as {@link LocalDateTime}, the Avro contract as {@link Instant}.
     */
    private static Instant toInstant(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
    }

    /** Kafka topics this service publishes to. Kept here so no string is typed twice. */
    static final class Topics {

        static final String USER_CREATED = "user.created";
        static final String USER_UPDATED = "user.updated";

        private Topics() {
        }
    }
}
