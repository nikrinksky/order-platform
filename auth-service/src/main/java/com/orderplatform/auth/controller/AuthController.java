/**
 * Controller for Auth Service authentication endpoints.
 * Provides endpoints for user registration, login, token refresh, and user info retrieval.
 */
package com.orderplatform.auth.controller;

import com.orderplatform.auth.AuthConstants;
import com.orderplatform.auth.dto.LoginRequest;
import com.orderplatform.auth.dto.RegisterRequest;
import com.orderplatform.auth.dto.UserDto;
import com.orderplatform.auth.model.User;
import com.orderplatform.auth.repository.UserRepository;
import com.orderplatform.auth.service.AuthenticationService;
import com.orderplatform.auth.service.UserService;
import com.orderplatform.auth.service.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for Auth Service authentication endpoints.
 * Provides endpoints for user registration, login, token refresh, and user info retrieval.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * Registers a new user.
     *
     * @param request the registration request containing user details
     * @return the registered user DTO
     */
    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        try {
            UserDto registeredUser = userService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(registeredUser);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Authenticates a user and returns tokens.
     *
     * @param request the login request containing email and password
     * @return a map containing access token, refresh token, and user info
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        try {
            Map<String, Object> response = authenticationService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid email or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    /**
     * Refreshes the access token using refresh token.
     *
     * @param refreshToken the refresh token from Authorization header
     * @return a map containing new access token, refresh token, and user info
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(String refreshToken) {
        try {
            if (refreshToken != null && refreshToken.startsWith("Bearer ")) {
                String token = refreshToken.substring(AuthConstants.BEARER_PREFIX_LENGTH);
                Map<String, Object> response = authenticationService.refreshToken(token);
                return ResponseEntity.ok(response);
            }
            throw new RuntimeException("Invalid refresh token");
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid refresh token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    /**
     * Retrieves the current authenticated user information.
     *
     * @param userDetails the authenticated user details
     * @return the user DTO for the current user
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(userMapper.toDto(user));
    }

    /**
     * Test endpoint to verify service is working.
     *
     * @return a success message
     */
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Auth service is working!");
    }

    /**
     * Logs out the current user by revoking tokens.
     *
     * @param authHeader the Authorization header with access token
     * @param refreshTokenFromHeader the optional refresh token from X-Refresh-Token header
     * @return a map containing logout status
     */
    @Operation(summary = "Logout", description = "Invalidates both access and refresh tokens")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully logged out"),
            @ApiResponse(responseCode = "401", description = "Invalid token")
    })
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(String authHeader,
            String refreshTokenFromHeader) {

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String accessToken = authHeader.substring(AuthConstants.BEARER_PREFIX_LENGTH);

                String refreshToken = refreshTokenFromHeader;

                authenticationService.logout(accessToken, refreshToken);

                Map<String, String> response = new HashMap<>();
                response.put("message", "Logout successful");
                response.put("accessTokenRevoked", "true");
                response.put("refreshTokenRevoked", refreshToken != null ? "true" : "false");

                return ResponseEntity.ok(response);
            }

            Map<String, String> error = new HashMap<>();
            error.put("error", "No access token provided");
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Logout failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
