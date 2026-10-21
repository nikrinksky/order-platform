package com.orderplatform.product.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Decides who may see products that are not published yet.
 *
 * <p>The catalogue endpoints are public, so an anonymous visitor reaches them without a token.
 * Visibility therefore cannot be expressed as a URL rule: the same endpoint must return the
 * published products to a visitor and the whole catalogue to an administrator.
 */
@Component
public class CatalogAccess {

    /** Roles that may see products hidden from the storefront. */
    private static final Set<String> PRIVILEGED_ROLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER");

    /**
     * Whether the current caller may see hidden (not published) products.
     *
     * @return true for an authenticated administrator or manager, false otherwise
     */
    public boolean canViewHiddenProducts() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PRIVILEGED_ROLES::contains);
    }
}
