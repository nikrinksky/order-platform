/**
 * Constants for Auth Service.
 * Defines magic numbers used throughout the application.
 */
package com.orderplatform.auth;

/**
 * Constants for Auth Service.
 * Defines magic numbers used throughout the application.
 */
public final class AuthConstants {

    /**
     * Access token expiration time in milliseconds (1 hour).
     */
    public static final long ACCESS_TOKEN_EXPIRATION = 3600000L;

    /**
     * Refresh token expiration time in milliseconds (7 days).
     */
    public static final long REFRESH_TOKEN_EXPIRATION = 604800000L;

    /**
     * Bearer token prefix length.
     */
    public static final int BEARER_PREFIX_LENGTH = 7;

    /**
     * Minimum password length.
     */
    public static final int MIN_PASSWORD_LENGTH = 6;

    /**
     * Token preview length for logging (20 characters).
     */
    public static final int TOKEN_PREVIEW_LENGTH = 20;

    /**
     * Milliseconds to seconds conversion factor.
     */
    public static final long MILLISECONDS_PER_SECOND = 1000;

    private AuthConstants() {
        // Prevent instantiation
    }
}
