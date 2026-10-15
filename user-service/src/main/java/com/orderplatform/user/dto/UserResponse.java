package com.orderplatform.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Read model of a user as returned by the REST API.
 *
 * <p>Deliberately a plain DTO rather than the Avro event: the event carries {@code CharSequence}
 * and {@code Instant} because that is what the wire format needs, and it has no business leaking
 * into a JSON response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
