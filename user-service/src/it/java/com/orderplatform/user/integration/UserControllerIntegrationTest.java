package com.orderplatform.user.integration;

import com.orderplatform.user.AbstractUserIntegrationTest;
import com.orderplatform.user.model.User;
import com.orderplatform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration тест для User Controller.
 */
@AutoConfigureMockMvc
class UserControllerIntegrationTest extends AbstractUserIntegrationTest {
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldGetAllUsers() throws Exception {
        userRepository.save(User.builder()
                .username("testuser_" + UUID.randomUUID().toString().substring(0, 8))
                .email("test" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .firstName("Test")
                .lastName("User")
                .isActive(true)
                .build());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Test"));
    }

    @Test
    void shouldGetUserById() throws Exception {
        User user = userRepository.save(User.builder()
                .username("getbyid_" + UUID.randomUUID().toString().substring(0, 8))
                .email("getbyid" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .firstName("Get")
                .lastName("ById")
                .isActive(true)
                .build());

        mockMvc.perform(get("/api/users/" + user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Get"));
    }
}