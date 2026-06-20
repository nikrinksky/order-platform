/**
 * DTO for user created event.
 */
package com.orderplatform.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO for user created event.
 * Used for Kafka messaging when a new user is registered.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedEvent {

    /**
     * User's unique identifier.
     */
    private String id;

    /**
     * User's email address.
     */
    private String email;

    /**
     * User's first name.
     */
    private String firstName;

    /**
     * User's last name.
     */
    private String lastName;

    /**
     * Set of user's roles.
     */
    private Set<String> roles;

    /**
     * Indicates if user account is active.
     */
    private boolean isActive;

    /**
     * Timestamp when user was created.
     */
    private LocalDateTime createdAt;
}
