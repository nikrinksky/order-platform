/**
 * Service for authentication operations.
 * Handles user login, token refresh, and logout.
 */
package com.orderplatform.auth.service;

import com.orderplatform.auth.AuthConstants;
import com.orderplatform.auth.dto.LoginRequest;
import com.orderplatform.auth.model.User;
import com.orderplatform.auth.repository.UserRepository;
import com.orderplatform.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for authentication operations.
 * Handles user login, token refresh, and logout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Authenticates a user and generates tokens.
     *
     * @param request the login request containing email and password
     * @return a map containing access token, refresh token, and user info
     */
    public Map<String, Object> login(LoginRequest request) {
        try {
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

            if (!user.isActive()) {
                throw new DisabledException("User account is disabled");
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(),
                            request.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());

            String accessToken = jwtService.generateToken(userDetails);
            String refreshToken = jwtService.generateRefreshToken(userDetails);

            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            log.info("User logged in successfully: {}", request.getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", AuthConstants.ACCESS_TOKEN_EXPIRATION);
            response.put("refreshExpiresIn", AuthConstants.REFRESH_TOKEN_EXPIRATION);
            response.put("user", userMapper.toDto(user));

            return response;
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for email: {}", request.getEmail());
            throw new BadCredentialsException("Invalid email or password");
        } catch (DisabledException e) {
            log.warn("Disabled account login attempt: {}", request.getEmail());
            throw new DisabledException("User account is disabled");
        }
    }

    /**
     * Refreshes access token using refresh token.
     * Invalidates the old access token and refresh token.
     *
     * @param accessToken the current access token (to be revoked)
     * @param refreshToken the refresh token (to be revoked)
     * @return a map containing new access token, refresh token, and user info
     */
    public Map<String, Object> refreshToken(String accessToken, String refreshToken) {
        try {
            // Check token type FIRST to prevent access token exchange
            if (!jwtService.isRefreshToken(refreshToken)) {
                log.warn("Invalid token type: expected refresh token, got access token");
                throw new RuntimeException("Invalid token type: expected refresh token");
            }

            if (tokenBlacklistService.isTokenBlacklisted(refreshToken)) {
                String preview = refreshToken.substring(0, Math.min(AuthConstants.TOKEN_PREVIEW_LENGTH,
                        refreshToken.length()));
                log.warn("Refresh token is blacklisted: {}...", preview);
                throw new RuntimeException("Refresh token has been revoked");
            }

            String userEmail = jwtService.extractUsername(refreshToken);

            if (userEmail == null) {
                throw new RuntimeException("Invalid refresh token");
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!user.isActive()) {
                throw new RuntimeException("User account is disabled");
            }

            if (!jwtService.isRefreshTokenValid(refreshToken)) {
                throw new RuntimeException("Refresh token expired or invalid");
            }

            // Generate new tokens FIRST before revoking old ones
            String newAccessToken = jwtService.generateToken(userDetails);
            String newRefreshToken = jwtService.generateRefreshToken(userDetails);

            // Revoke old access token
            if (accessToken != null) {
                long accessExpiration = jwtService.getExpirationFromToken(accessToken);
                long accessRemainingTtl = (accessExpiration - System.currentTimeMillis()) / 1000;
                if (accessRemainingTtl > 0) {
                    tokenBlacklistService.blacklistToken(accessToken, accessRemainingTtl);
                    log.debug("Old access token blacklisted for user: {}", userEmail);
                }
            }

            // Don't revoke old refresh token here - it should remain valid until logout
            // This allows multiple refresh calls with the same refresh token
            log.debug("Old refresh token kept valid for user: {}. New refresh token issued.", userEmail);

            log.info("Token refreshed successfully for user: {}. Old access token revoked.",
                    userEmail);

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", newAccessToken);
            response.put("refreshToken", newRefreshToken);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", AuthConstants.ACCESS_TOKEN_EXPIRATION);
            response.put("refreshExpiresIn", AuthConstants.REFRESH_TOKEN_EXPIRATION);
            response.put("user", userMapper.toDto(user));

            return response;
        } catch (RuntimeException e) {
            // Re-throw RuntimeException with original message
            log.error("Refresh token runtime error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Refresh token validation error: {}", e.getMessage());
            throw new RuntimeException("Invalid refresh token");
        }
    }

    /**
     * Logs out a user by revoking both tokens.
     *
     * @param accessToken the access token to revoke
     * @param refreshToken the refresh token to revoke
     */
    public void logout(String accessToken, String refreshToken) {
        try {
            String userEmail = jwtService.extractUsername(accessToken);

            long accessExpiration = jwtService.getExpirationFromToken(accessToken);
            long accessRemainingTtl = (accessExpiration - System.currentTimeMillis()) / 1000;
            if (accessRemainingTtl > 0) {
                tokenBlacklistService.blacklistToken(accessToken, accessRemainingTtl);
            }

            if (refreshToken != null && !refreshToken.isEmpty()) {
                long refreshExpiration = jwtService.getExpirationFromToken(refreshToken);
                long refreshRemainingTtl = (refreshExpiration - System.currentTimeMillis()) / 1000;
                if (refreshRemainingTtl > 0) {
                    tokenBlacklistService.blacklistToken(refreshToken, refreshRemainingTtl);
                }
            }

            log.info("User logged out successfully: {}", userEmail);
        } catch (Exception e) {
            log.error("Logout error: {}", e.getMessage());
        }
    }
}
