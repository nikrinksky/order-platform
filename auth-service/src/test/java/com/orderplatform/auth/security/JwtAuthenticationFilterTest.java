package com.orderplatform.auth.security;

import com.orderplatform.auth.model.Role;
import com.orderplatform.auth.model.User;
import com.orderplatform.auth.repository.UserRepository;
import com.orderplatform.auth.service.CustomUserDetailsService;
import com.orderplatform.auth.service.TokenBlacklistService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        userDetails = org.springframework.security.core.userdetails.User
                .withUsername("test@example.com")
                .password("encoded")
                .authorities("ROLE_USER")
                .build();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipWhenNoAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldSkipWhenHeaderIsNotBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldAuthenticateUserWithValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid.jwt.token");
        request.setServletPath("/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractUsername("valid.jwt.token")).thenReturn("test@example.com");
        when(tokenBlacklistService.isTokenBlacklisted("valid.jwt.token")).thenReturn(false);
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        User user = User.builder().email("test@example.com").roles(Set.of(Role.ROLE_USER)).isActive(true).build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtService.isAccessTokenValid("valid.jwt.token", userDetails)).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldRejectBlacklistedToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer blacklisted.token");
        request.setServletPath("/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractUsername("blacklisted.token")).thenReturn("test@example.com");
        when(tokenBlacklistService.isTokenBlacklisted("blacklisted.token")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldSkipBlacklistCheckForRefreshEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer refresh.token");
        request.setServletPath("/api/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractUsername("refresh.token")).thenReturn("test@example.com");
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        User user = User.builder().email("test@example.com").roles(Set.of(Role.ROLE_USER)).isActive(true).build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtService.isAccessTokenValid("refresh.token", userDetails)).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(tokenBlacklistService, never()).isTokenBlacklisted("refresh.token");
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void shouldSendUnauthorizedOnJwtException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.jwt.token");
        request.setServletPath("/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractUsername("invalid.jwt.token")).thenThrow(new JwtException("Invalid token"));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldSendErrorOnGenericException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer error.token");
        request.setServletPath("/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.extractUsername("error.token")).thenReturn("test@example.com");
        when(tokenBlacklistService.isTokenBlacklisted("error.token")).thenReturn(false);
        when(userDetailsService.loadUserByUsername(anyString())).thenThrow(new RuntimeException("DB down"));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(500, response.getStatus());
    }
}