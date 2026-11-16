/**
 * Service for authentication operations.
 * Handles user login, token refresh, and logout.
 */
package com.orderplatform.auth.service;

import com.orderplatform.auth.AuthConstants;
import com.orderplatform.auth.dto.LoginRequest;
import com.orderplatform.auth.model.Role;
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

            Map<String, Object> extraClaims = new HashMap<>();
            extraClaims.put(JwtService.USER_ID_CLAIM, user.getId());
            extraClaims.put(JwtService.ROLES_CLAIM, user.getRoles().stream()
                    .map(Role::getAuthority)
                    .toList());

            String accessToken = jwtService.generateToken(extraClaims, userDetails);
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
     * <p>The refresh token is rotated: the one that came in is revoked together with the access
     * token it replaces, so a token that leaks can be replayed only until the legitimate client
     * refreshes. Reusing a revoked refresh token is answered with an error, which is the signal
     * that the token has to be treated as stolen.
     *
     * <p>The new pair is generated before the old one is revoked, so a failure while generating
     * leaves the old pair usable. Revocation itself is fail-closed: if the blacklist cannot be
     * written the new pair is not returned, because it would replace tokens that are still alive.
     *
     * <p>The access token is normally expired by the time this is called, so its expiration is
     * read in a way that tolerates that; it is only used to size the blacklist entry.
     *
     * @param accessToken the current access token (to be revoked), may be expired
     * @param refreshToken the refresh token (to be revoked after the new one is issued)
     * @return a map containing new access token, refresh token, and user info
     * @throws com.orderplatform.auth.exception.TokenRevocationUnavailableException when the old
     *         tokens could not be revoked
     */
    public Map<String, Object> refreshToken(String accessToken, String refreshToken) {
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

        // Generate new tokens FIRST, so a failure here leaves the old pair usable
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put(JwtService.USER_ID_CLAIM, user.getId());
        extraClaims.put(JwtService.ROLES_CLAIM, user.getRoles().stream()
                .map(Role::getAuthority)
                .toList());

        String newAccessToken = jwtService.generateToken(extraClaims, userDetails);
        String newRefreshToken = jwtService.generateRefreshToken(userDetails);

        revoke(accessToken, "access");
        revoke(refreshToken, "refresh");

        log.info("Token refreshed successfully for user: {}; the old token pair was revoked",
                userEmail);

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", newAccessToken);
        response.put("refreshToken", newRefreshToken);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", AuthConstants.ACCESS_TOKEN_EXPIRATION);
        response.put("refreshExpiresIn", AuthConstants.REFRESH_TOKEN_EXPIRATION);
        response.put("user", userMapper.toDto(user));

        return response;
    }

    /**
     * Blacklists a token for the rest of its lifetime.
     *
     * <p>Two failures are told apart on purpose. A token whose expiration cannot be read is not
     * revoked: it is either malformed or already expired, and in both cases the signature and
     * expiry checks reject it without any blacklist entry. A blacklist that cannot be written,
     * however, is fatal for the caller - it is reported and answered with {@code 503}, because
     * returning success would leave a token usable that the client believes revoked.
     *
     * @param token token to revoke, may be null or already expired
     * @param kind token kind, used for logging only
     * @throws com.orderplatform.auth.exception.TokenRevocationUnavailableException when the
     *         entry could not be written
     */
    private void revoke(String token, String kind) {
        if (token == null || token.isEmpty()) {
            return;
        }

        long expiration;
        try {
            expiration = jwtService.getExpirationFromTokenAllowingExpired(token);
        } catch (Exception e) {
            log.warn("Could not read the expiration of the old {} token, nothing to revoke: {}",
                    kind, e.getMessage());
            return;
        }

        long remainingTtl = (expiration - System.currentTimeMillis())
                / AuthConstants.MILLISECONDS_PER_SECOND;
        if (remainingTtl <= 0) {
            log.debug("Old {} token has expired, no blacklist entry needed", kind);
            return;
        }

        tokenBlacklistService.blacklistToken(token, remainingTtl);
        log.debug("Old {} token blacklisted", kind);
    }

    /**
     * Logs out a user by revoking both tokens.
     *
     * <p>Fail-closed: when the blacklist cannot be written the failure is propagated, so the
     * caller answers {@code 503} instead of reporting a logout that did not happen.
     *
     * @param accessToken the access token to revoke
     * @param refreshToken the refresh token to revoke
     * @throws com.orderplatform.auth.exception.TokenRevocationUnavailableException when a token
     *         could not be revoked
     */
    public void logout(String accessToken, String refreshToken) {
        String userEmail = null;
        try {
            userEmail = jwtService.extractUsername(accessToken);
        } catch (Exception e) {
            // An expired access token must not stop the refresh token from being revoked
            log.warn("Could not read the user from the access token on logout: {}", e.getMessage());
        }

        revoke(accessToken, "access");
        revoke(refreshToken, "refresh");

        log.info("User logged out successfully: {}", userEmail);
    }
}
