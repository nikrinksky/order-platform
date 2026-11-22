package com.orderplatform.product.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for CatalogAccess.
 *
 * <p>The class is mocked in {@link com.orderplatform.product.service.ProductServiceTest}, so without
 * these tests its own branches were never executed and the module missed its branch coverage gate.
 */
class CatalogAccessTest {

    private final CatalogAccess catalogAccess = new CatalogAccess();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldHideHiddenProductsWhenNoAuthenticationIsPresent() {
        assertFalse(catalogAccess.canViewHiddenProducts());
    }

    @Test
    void shouldHideHiddenProductsFromAnonymousVisitor() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "anonymous-key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        assertFalse(catalogAccess.canViewHiddenProducts());
    }

    /**
     * A token that exists but was never authenticated (the two-argument constructor) must not be
     * treated as a caller, otherwise an unverified token would grant the privileged view.
     */
    @Test
    void shouldHideHiddenProductsFromUnauthenticatedToken() {
        UsernamePasswordAuthenticationToken unauthenticated =
                new UsernamePasswordAuthenticationToken("visitor", "credentials");
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);

        assertFalse(catalogAccess.canViewHiddenProducts());
    }

    @Test
    void shouldHideHiddenProductsFromRegularUser() {
        authenticate("visitor", "ROLE_USER");

        assertFalse(catalogAccess.canViewHiddenProducts());
    }

    @Test
    void shouldShowHiddenProductsToAdmin() {
        authenticate("admin", "ROLE_ADMIN");

        assertTrue(catalogAccess.canViewHiddenProducts());
    }

    @Test
    void shouldShowHiddenProductsToManager() {
        authenticate("manager", "ROLE_MANAGER");

        assertTrue(catalogAccess.canViewHiddenProducts());
    }

    @Test
    void shouldShowHiddenProductsWhenOneOfSeveralRolesIsPrivileged() {
        authenticate("admin", "ROLE_USER", "ROLE_ADMIN");

        assertTrue(catalogAccess.canViewHiddenProducts());
    }

    private static void authenticate(String name, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(name, null, authorities));
    }
}
