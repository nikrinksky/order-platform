package com.orderplatform.auth.config;

import com.orderplatform.auth.security.JwtAuthenticationFilter;
import com.orderplatform.auth.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(mock(CustomUserDetailsService.class), mock(JwtAuthenticationFilter.class));
    }

    @Test
    void shouldCreatePasswordEncoder() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        assertNotNull(encoder);
        assertTrue(encoder.matches("pass", encoder.encode("pass")));
    }

    @Test
    void shouldCreateAuthenticationProvider() {
        AuthenticationProvider provider = securityConfig.authenticationProvider();

        assertNotNull(provider);
        assertInstanceOf(DaoAuthenticationProvider.class, provider);
    }

    @Test
    void shouldCreateSecurityFilterChain() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class, RETURNS_SELF);
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
        when(http.build()).thenReturn(chain);

        SecurityFilterChain result = securityConfig.securityFilterChain(http);

        assertNotNull(result);
        verify(http).csrf(any());
        verify(http).authorizeHttpRequests(any());
    }

    @Test
    void shouldCreateAuthenticationManager() throws Exception {
        AuthenticationConfiguration config = mock(AuthenticationConfiguration.class);
        when(config.getAuthenticationManager()).thenReturn(mock(org.springframework.security.authentication.AuthenticationManager.class));

        assertNotNull(securityConfig.authenticationManager(config));
    }
}