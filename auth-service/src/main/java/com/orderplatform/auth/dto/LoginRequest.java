/**
 * DTO for user login request.
 */
package com.orderplatform.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user login request.
 * Contains email and password for authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * User's email address.
     * Must be valid email format and not blank.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * User's password.
     * Must not be blank.
     */
    @NotBlank(message = "Password is required")
    private String password;
}
