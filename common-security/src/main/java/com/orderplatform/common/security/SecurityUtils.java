package com.orderplatform.common.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for accessing the currently authenticated user.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Returns the ID of the currently authenticated user.
     * <p>
     * The principal is set by {@link JwtAuthenticationFilter} to the user ID
     * extracted from the JWT "userId" claim.
     *
     * @return current user ID
     * @throws IllegalStateException if no authenticated user is present
     */
    public static String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("User is not authenticated");
        }
        return authentication.getName();
    }
}