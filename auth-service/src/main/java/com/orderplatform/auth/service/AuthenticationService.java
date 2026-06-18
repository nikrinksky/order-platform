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
     *
     * @param refreshToken the refresh token
     * @return a map containing new access token, refresh token, and user info
     */
    public Map<String, Object> refreshToken(String refreshToken) {
        try {
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

            if (!jwtService.isTokenValid(refreshToken, userDetails)) {
                throw new RuntimeException("Refresh token expired or invalid");
            }

            long oldRefreshExpiration = jwtService.getExpirationFromToken(refreshToken);
            tokenBlacklistService.blacklistToken(refreshToken, oldRefreshExpiration);
            log.debug("Old refresh token blacklisted for user: {}", userEmail);

            String newAccessToken = jwtService.generateToken(userDetails);
            String newRefreshToken = jwtService.generateRefreshToken(userDetails);

            log.info("Token refreshed successfully for user: {}. Old refresh token revoked.",
                    userEmail);

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", newAccessToken);
            response.put("refreshToken", newRefreshToken);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", AuthConstants.ACCESS_TOKEN_EXPIRATION);
            response.put("refreshExpiresIn", AuthConstants.REFRESH_TOKEN_EXPIRATION);
            response.put("user", userMapper.toDto(user));

            return response;
        } catch (Exception e) {
            log.error("Refresh token error: {}", e.getMessage());
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
            tokenBlacklistService.blacklistToken(accessToken, accessExpiration);

            if (refreshToken != null && !refreshToken.isEmpty()) {
                long refreshExpiration = jwtService.getExpirationFromToken(refreshToken);
                tokenBlacklistService.blacklistToken(refreshToken, refreshExpiration);
            }

            log.info("User logged out successfully: {}", userEmail);
        } catch (Exception e) {
            log.error("Logout error: {}", e.getMessage());
        }
    }
}
