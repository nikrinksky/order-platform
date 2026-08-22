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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(jwtService.generateToken(any(Map.class), eq(userDetails))).thenReturn("access-token");
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
        when(jwtService.generateToken(any(Map.class), eq(userDetails))).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(userDetails)).thenReturn("new-refresh-token");

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

    @Test
    void testLogin_Fail_UserNotFound() {
        LoginRequest request = LoginRequest.builder()
                .email("missing@example.com")
                .password("password123")
                .build();

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> authenticationService.login(request));
    }

    @Test
    void testLogin_Fail_DisabledUser() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();
        testUser.setActive(false);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        assertThrows(org.springframework.security.authentication.DisabledException.class,
                () -> authenticationService.login(request));
    }

    @Test
    void testLogin_Fail_BadCredentials() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("wrong-password")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> authenticationService.login(request));
    }

    @Test
    void testRefreshToken_Fail_InvalidUsername() {
        String refreshToken = "refresh-token";
        when(jwtService.isRefreshToken(refreshToken)).thenReturn(true);
        when(tokenBlacklistService.isTokenBlacklisted(refreshToken)).thenReturn(false);
        when(jwtService.extractUsername(refreshToken)).thenReturn(null);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> authenticationService.refreshToken("access-token", refreshToken));

        assertThat(thrown.getMessage()).isEqualTo("Invalid refresh token");
    }

    @Test
    void testRefreshToken_Fail_UserNotFound() {
        String refreshToken = "refresh-token";
        when(jwtService.isRefreshToken(refreshToken)).thenReturn(true);
        when(tokenBlacklistService.isTokenBlacklisted(refreshToken)).thenReturn(false);
        when(jwtService.extractUsername(refreshToken)).thenReturn("missing@example.com");
        when(userDetailsService.loadUserByUsername("missing@example.com")).thenReturn(userDetails);
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> authenticationService.refreshToken("access-token", refreshToken));

        assertThat(thrown.getMessage()).isEqualTo("User not found");
    }

    @Test
    void testRefreshToken_Fail_DisabledUser() {
        String refreshToken = "refresh-token";
        testUser.setActive(false);
        when(jwtService.isRefreshToken(refreshToken)).thenReturn(true);
        when(tokenBlacklistService.isTokenBlacklisted(refreshToken)).thenReturn(false);
        when(jwtService.extractUsername(refreshToken)).thenReturn("test@example.com");
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> authenticationService.refreshToken("access-token", refreshToken));

        assertThat(thrown.getMessage()).isEqualTo("User account is disabled");
    }

    @Test
    void testRefreshToken_Fail_ExpiredRefreshToken() {
        String refreshToken = "expired-refresh-token";
        when(jwtService.isRefreshToken(refreshToken)).thenReturn(true);
        when(tokenBlacklistService.isTokenBlacklisted(refreshToken)).thenReturn(false);
        when(jwtService.extractUsername(refreshToken)).thenReturn("test@example.com");
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.isRefreshTokenValid(refreshToken)).thenReturn(false);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> authenticationService.refreshToken("access-token", refreshToken));

        assertThat(thrown.getMessage()).isEqualTo("Refresh token expired or invalid");
    }

    @Test
    void testRefreshToken_Success_RevokesExpiredAccessToken() {
        String oldAccessToken = "expired-access-token";
        String oldRefreshToken = "old-refresh-token";

        when(jwtService.extractUsername(oldRefreshToken)).thenReturn("test@example.com");
        when(tokenBlacklistService.isTokenBlacklisted(oldRefreshToken)).thenReturn(false);
        when(jwtService.isRefreshToken(oldRefreshToken)).thenReturn(true);
        when(jwtService.isRefreshTokenValid(oldRefreshToken)).thenReturn(true);
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any(Map.class), eq(userDetails))).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(userDetails)).thenReturn("new-refresh-token");
        when(jwtService.getExpirationFromToken(oldAccessToken)).thenReturn(System.currentTimeMillis() - 1000);

        Map<String, Object> response = authenticationService.refreshToken(oldAccessToken, oldRefreshToken);

        assertThat(response.get("accessToken")).isEqualTo("new-access-token");
        org.mockito.Mockito.verify(tokenBlacklistService, org.mockito.Mockito.never())
                .blacklistToken(oldAccessToken, 0);
    }

    @Test
    void testLogout_ShouldRevokeBothTokens() {
        String accessToken = "access-token";
        String refreshToken = "refresh-token";

        when(jwtService.extractUsername(accessToken)).thenReturn("test@example.com");
        when(jwtService.getExpirationFromToken(accessToken))
                .thenReturn(System.currentTimeMillis() + 100000);
        when(jwtService.getExpirationFromToken(refreshToken))
                .thenReturn(System.currentTimeMillis() + 100000);

        assertThatCode(() -> authenticationService.logout(accessToken, refreshToken))
                .doesNotThrowAnyException();
    }

    @Test
    void testLogout_ShouldHandleException() {
        String accessToken = "invalid-token";

        when(jwtService.extractUsername(accessToken)).thenThrow(new RuntimeException("JWT error"));

        assertThatCode(() -> authenticationService.logout(accessToken, null))
                .doesNotThrowAnyException();
    }
}
