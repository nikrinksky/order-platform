/**
 * DTO for updating a user's roles.
 */
package com.orderplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request body for assigning roles to a user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRolesRequest {

    /**
     * Roles to assign, e.g. {@code ["ROLE_USER", "ROLE_MANAGER"]}. Must not be empty: the
     * endpoint replaces the whole set, so an empty set would strip every role.
     */
    @NotEmpty(message = "At least one role is required")
    private Set<@NotBlank(message = "Role must not be blank") String> roles;
}
