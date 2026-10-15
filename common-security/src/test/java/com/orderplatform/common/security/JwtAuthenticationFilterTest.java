package com.orderplatform.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenBlacklistChecker tokenBlacklistChecker;

    private ObjectProvider<TokenBlacklistChecker> checkerProvider;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        checkerProvider = mock(ObjectProvider.class);
        // Not every test reaches the lookup, so the stub is lenient on purpose
        lenient().when(checkerProvider.getIfAvailable()).thenReturn(tokenBlacklistChecker);
        filter = new JwtAuthenticationFilter(jwtService, checkerProvider);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some.jwt.token");
        request.setRequestURI("/api/orders");
        return request;
    }

    @Test
    void shouldAuthenticateWhenTokenIsValidAndNotRevoked() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.isValidAccessToken("some.jwt.token")).thenReturn(true);
        when(tokenBlacklistChecker.isBlacklisted("some.jwt.token")).thenReturn(false);
        when(jwtService.extractUserId("some.jwt.token")).thenReturn("user-1");
        when(jwtService.extractRoles("some.jwt.token")).thenReturn(List.of("ROLE_USER"));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("user-1");
    }

    /**
     * A token revoked on logout must stop working immediately, not when it expires.
     */
    @Test
    void shouldRejectRevokedToken() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.isValidAccessToken("some.jwt.token")).thenReturn(true);
        when(tokenBlacklistChecker.isBlacklisted("some.jwt.token")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.isValidAccessToken("some.jwt.token")).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    /**
     * The blacklist is optional: a service without Redis keeps authenticating tokens.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldAuthenticateWhenNoBlacklistIsConfigured() throws Exception {
        ObjectProvider<TokenBlacklistChecker> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        JwtAuthenticationFilter filterWithoutBlacklist =
                new JwtAuthenticationFilter(jwtService, emptyProvider);

        MockHttpServletRequest request = requestWithToken();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.isValidAccessToken("some.jwt.token")).thenReturn(true);
        when(jwtService.extractUserId("some.jwt.token")).thenReturn("user-1");
        when(jwtService.extractRoles("some.jwt.token")).thenReturn(List.of("ROLE_USER"));

        filterWithoutBlacklist.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void shouldSkipRequestsWithoutBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(jwtService, never()).isValidAccessToken(anyString());
    }
}
