package com.orderplatform.auth.service;

import com.orderplatform.auth.dto.LoginRequest;
import com.orderplatform.auth.model.Role;
import com.orderplatform.auth.model.User;
import com.orderplatform.auth.repository.UserRepository;
import com.orderplatform.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private AuthenticationService authenticationService;

    private User testUser;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("123")
                .email("test@example.com")
                .password("encoded")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of(Role.ROLE_USER))
                .isActive(true)
                .build();

        userDetails = org.springframework.security.core.userdetails.User
                .withUsername("test@example.com")
                .password("encoded")
                .authorities("ROLE_USER")
                .build();
    }

    @Test
    void testLogin_Success() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "test@example.com", "password123");

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(userDetails)).thenReturn("refresh-token");

        Map<String, Object> response = authenticationService.login(request);

        assertThat(response).containsKey("accessToken");
        assertThat(response).containsKey("refreshToken");
        assertThat(response.get("accessToken")).isEqualTo("access-token");
    }

    @Test
    void testRefreshToken_Success() {
        String oldAccessToken = "old-access-token";
        String oldRefreshToken = "old-refresh-token";

        when(jwtService.extractUsername(oldRefreshToken)).thenReturn("test@example.com");
        when(tokenBlacklistService.isTokenBlacklisted(oldRefreshToken)).thenReturn(false);
        when(jwtService.isRefreshToken(oldRefreshToken)).thenReturn(true);
        when(jwtService.isRefreshTokenValid(oldRefreshToken)).thenReturn(true);
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(userDetails)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(userDetails)).thenReturn("new-refresh-token");
        when(jwtService.getExpirationFromToken(oldRefreshToken)).thenReturn(System.currentTimeMillis() + 3600000);
        when(jwtService.getExpirationFromToken(oldAccessToken)).thenReturn(System.currentTimeMillis() + 300000);

        Map<String, Object> response = authenticationService.refreshToken(oldAccessToken, oldRefreshToken);

        assertThat(response).containsKey("accessToken");
        assertThat(response).containsKey("refreshToken");
        assertThat(response.get("accessToken")).isEqualTo("new-access-token");
        assertThat(response.get("refreshToken")).isEqualTo("new-refresh-token");
    }

    @Test
    void testRefreshToken_Fail_WhenRefreshTokenTypeInvalid() {
        String oldAccessToken = "old-access-token";
        String invalidToken = "access-token";

        when(jwtService.isRefreshToken(invalidToken)).thenReturn(false);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            authenticationService.refreshToken(oldAccessToken, invalidToken);
        });

        assertThat(thrown.getMessage()).isEqualTo("Invalid token type: expected refresh token");
    }

    @Test
    void testRefreshToken_Fail_WhenRefreshTokenBlacklisted() {
        String oldAccessToken = "old-access-token";
        String blacklistedRefreshToken = "blacklisted-refresh-token";

        when(jwtService.isRefreshToken(blacklistedRefreshToken)).thenReturn(true);
        when(tokenBlacklistService.isTokenBlacklisted(blacklistedRefreshToken)).thenReturn(true);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            authenticationService.refreshToken(oldAccessToken, blacklistedRefreshToken);
        });

        assertThat(thrown.getMessage()).isEqualTo("Refresh token has been revoked");
    }
}
