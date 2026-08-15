package com.orderplatform.user.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.user.model.User;
import com.orderplatform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration тест для User Controller.
 */
@AutoConfigureMockMvc
class UserControllerIntegrationTest extends AbstractUserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateUser() throws Exception {
        String userJson = """
                {
                    "email": "test@example.com",
                    "password": "testpass123",
                    "firstName": "Test",
                    "lastName": "User"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("Test"));
    }

    @Test
    void shouldRejectUserWithoutEmail() throws Exception {
        String userJson = """
                {
                    "email": "",
                    "password": "testpass123",
                    "firstName": "Test",
                    "lastName": "User"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson))
                .andExpect(status().isBadRequest());
    }
}
