/**
 * DTO for authentication response.
 */
package com.orderplatform.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for authentication response.
 * Contains tokens and user information after successful authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * Access token for API authorization.
     */
    private String accessToken;

    /**
     * Refresh token for obtaining new access tokens.
     */
    private String refreshToken;

    /**
     * Token type (typically "Bearer").
     */
    private String tokenType;

    /**
     * Access token expiration time in milliseconds.
     */
    private Long expiresIn;

    /**
     * User information.
     */
    private UserDto user;
}
