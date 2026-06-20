/**
 * DTO for user registration request.
 * Contains validation constraints for user input.
 */
package com.orderplatform.auth.dto;

import com.orderplatform.auth.AuthConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user registration request.
 * Contains validation constraints for user input.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    /**
     * User's email address.
     * Must be valid email format and not blank.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * User's password.
     * Must be at least 6 characters long and not blank.
     */
    @NotBlank(message = "Password is required")
    @Size(min = AuthConstants.MIN_PASSWORD_LENGTH,
            message = "Password must be at least " + AuthConstants.MIN_PASSWORD_LENGTH + " characters")
    private String password;

    /**
     * User's first name.
     * Must not be blank.
     */
    @NotBlank(message = "First name is required")
    private String firstName;

    /**
     * User's last name.
     * Must not be blank.
     */
    @NotBlank(message = "Last name is required")
    private String lastName;
}
