package com.orderplatform.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private JwtService jwtService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();

        // Используем reflection для установки приватных полей
        try {
            var secretField = JwtService.class.getDeclaredField("secret");
            secretField.setAccessible(true);
            secretField.set(jwtService, "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");

            var expirationField = JwtService.class.getDeclaredField("expiration");
            expirationField.setAccessible(true);
            expirationField.set(jwtService, 3600000L);

            var refreshExpirationField = JwtService.class.getDeclaredField("refreshExpiration");
            refreshExpirationField.setAccessible(true);
            refreshExpirationField.set(jwtService, 604800000L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("test@example.com");
    }

    @Test
    void testGenerateToken() {
        String token = jwtService.generateToken(userDetails);
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
    }

    @Test
    void testGenerateRefreshToken() {
        String token = jwtService.generateRefreshToken(userDetails);
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
    }

    @Test
    void testExtractUsername() {
        String token = jwtService.generateToken(userDetails);
        String username = jwtService.extractUsername(token);
        assertThat(username).isEqualTo("test@example.com");
    }

    @Test
    void testIsTokenValid() {
        String token = jwtService.generateToken(userDetails);
        boolean isValid = jwtService.isTokenValid(token, userDetails);
        assertThat(isValid).isTrue();
    }
}
