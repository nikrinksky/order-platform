package com.orderplatform.auth.controller;

import com.orderplatform.auth.dto.LoginRequest;
import com.orderplatform.auth.dto.RegisterRequest;
import com.orderplatform.auth.dto.UserDto;
import com.orderplatform.auth.service.AuthenticationService;
import com.orderplatform.auth.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private com.orderplatform.auth.repository.UserRepository userRepository;

    @MockBean
    private com.orderplatform.auth.service.UserMapper userMapper;

    @MockBean
    private com.orderplatform.auth.security.JwtService jwtService;

    @MockBean
    private com.orderplatform.auth.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.orderplatform.auth.service.CustomUserDetailsService customUserDetailsService;

    private RegisterRequest validRegisterRequest() {
        return RegisterRequest.builder()
                .email("test@example.com")
                .password("password123")
                .firstName("Test")
                .lastName("User")
                .build();
    }

    private UserDto sampleUserDto() {
        return UserDto.builder()
                .id("123")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();
    }

    @Test
    void testRegister_Success() throws Exception {
        RegisterRequest request = validRegisterRequest();

        when(userService.register(any(RegisterRequest.class))).thenReturn(sampleUserDto());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void testRegister_Conflict_ReturnsBadRequest() throws Exception {
        RegisterRequest request = validRegisterRequest();

        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new RuntimeException("User with email test@example.com already exists"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLogin_Success() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", "token123");
        response.put("refreshToken", "refresh123");
        response.put("tokenType", "Bearer");

        when(authenticationService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token123"));
    }

    @Test
    void testLogin_Fail_ReturnsUnauthorized() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("wrong-password")
                .build();

        when(authenticationService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void testRefresh_Success() throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", "new-token");
        response.put("refreshToken", "new-refresh");

        when(authenticationService.refreshToken("access-token", "refresh-token")).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer access-token")
                        .header("X-Refresh-Token", "Bearer refresh-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-token"));
    }

    @Test
    void testRefresh_MissingAccessToken_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Refresh-Token", "refresh-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRefresh_MissingRefreshToken_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRefresh_InvalidTokens_ReturnsUnauthorized() throws Exception {
        when(authenticationService.refreshToken("access-token", "refresh-token"))
                .thenThrow(new RuntimeException("Invalid token type"));

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer access-token")
                        .header("X-Refresh-Token", "refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid tokens: Invalid token type"));
    }

    @Test
    void testRefresh_PlainTokens_Works() throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", "new-token");

        when(authenticationService.refreshToken("access-token", "refresh-token")).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "access-token")
                        .header("X-Refresh-Token", "refresh-token"))
                .andExpect(status().isOk());
    }

    @Test
    void testEndpoint_ReturnsWorkingMessage() throws Exception {
        mockMvc.perform(get("/api/auth/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("Auth service is working!"));
    }

    @Test
    void testLogout_Success() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer access-token")
                        .header("X-Refresh-Token", "Bearer refresh-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));
    }

    @Test
    void testLogout_MissingHeader_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("X-Refresh-Token", "refresh-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLogout_Failure_ReturnsInternalServerError() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("Redis down"))
                .when(authenticationService).logout(anyString(), anyString());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer access-token")
                        .header("X-Refresh-Token", "refresh-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Logout failed: Redis down"));
    }
}