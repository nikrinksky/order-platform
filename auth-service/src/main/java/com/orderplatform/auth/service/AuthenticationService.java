package com.orderplatform.auth.service;

import com.orderplatform.auth.dto.LoginRequest;
import com.orderplatform.auth.dto.UserDto;
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

    public Map<String, Object> login(LoginRequest request) {
        try {
            // Проверка существования пользователя
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

            // Проверка активен ли пользователь
            if (!user.isActive()) {
                throw new DisabledException("User account is disabled");
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());

            String accessToken = jwtService.generateToken(userDetails);
            String refreshToken = jwtService.generateRefreshToken(userDetails);

            // Update last login
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            log.info("User logged in successfully: {}", request.getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", 3600000L);
            response.put("refreshExpiresIn", 604800000L);
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

    public Map<String, Object> refreshToken(String refreshToken) {
        try {
            // Проверка, не заблокирован ли refresh token
            if (tokenBlacklistService.isTokenBlacklisted(refreshToken)) {
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

            // Генерируем новые токены
            String newAccessToken = jwtService.generateToken(userDetails);
            String newRefreshToken = jwtService.generateRefreshToken(userDetails);

            // Инвалидируем старый refresh token
            tokenBlacklistService.blacklistToken(refreshToken, jwtService.getRefreshExpiration());

            log.info("Token refreshed successfully for user: {}", userEmail);

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", newAccessToken);
            response.put("refreshToken", newRefreshToken);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", 3600000L);
            response.put("refreshExpiresIn", 604800000L);
            response.put("user", userMapper.toDto(user));

            return response;
        } catch (Exception e) {
            log.error("Refresh token error: {}", e.getMessage());
            throw new RuntimeException("Invalid refresh token");
        }
    }

    public void logout(String accessToken, String refreshToken) {
        try {
            String userEmail = jwtService.extractUsername(accessToken);

            // Инвалидируем access token
            long accessExpiration = jwtService.getExpirationFromToken(accessToken);
            tokenBlacklistService.blacklistToken(accessToken, accessExpiration);

            // Инвалидируем refresh token
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