package com.orderplatform.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.user.model.Role;
import com.orderplatform.user.model.User;
import com.orderplatform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("test-user-123")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of(Role.ROLE_USER))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testGetAllUsers() throws Exception {
        // given
        when(userRepository.findAll()).thenReturn(List.of(testUser));

        // when & then
        mockMvc.perform(get("/api/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("test@example.com"))
                .andExpect(jsonPath("$[0].firstName").value("Test"));

        verify(userRepository, times(1)).findAll();
    }

    @Test
    void testGetUserById_Exists() throws Exception {
        // given
        when(userRepository.findById("test-user-123")).thenReturn(Optional.of(testUser));

        // when & then
        mockMvc.perform(get("/api/users/test-user-123")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(userRepository, times(1)).findById("test-user-123");
    }

    @Test
    void testGetUserById_NotFound() throws Exception {
        // given
        when(userRepository.findById(any(String.class))).thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/api/users/nonexistent-id")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(userRepository, times(1)).findById(any(String.class));
    }

    @Test
    void testGetUserByEmail_Exists() throws Exception {
        // given
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        // when & then
        mockMvc.perform(get("/api/users/email/test@example.com")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("Test"));

        verify(userRepository, times(1)).findByEmail("test@example.com");
    }

    @Test
    void testGetUserByEmail_NotFound() throws Exception {
        // given
        when(userRepository.findByEmail(any(String.class))).thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/api/users/email/nonexistent@example.com")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(userRepository, times(1)).findByEmail(any(String.class));
    }
}
