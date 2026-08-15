package com.orderplatform.user.integration;

import com.orderplatform.user.AbstractIntegrationTest;
import com.orderplatform.user.model.User;
import com.orderplatform.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // Очищаем базу перед каждым тестом
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Очищаем базу после каждого теста
        userRepository.deleteAll();
    }

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders getJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String generateUniqueEmail(String base) {
        return base + "_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    @Test
    void testGetAllUsers_EmptyDatabase() {
        ResponseEntity<User[]> response = restTemplate.getForEntity(
                getBaseUrl() + "/api/users",
                User[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().length).isEqualTo(0);
    }

    @Test
    void testGetAllUsers_WithUsers() {
        // Создаем тестового пользователя напрямую в БД
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username("testuser_" + UUID.randomUUID().toString().substring(0, 8))
                .email(generateUniqueEmail("test"))
                .firstName("Test")
                .lastName("User")
                .isActive(true)
                .build();
        userRepository.save(user);

        ResponseEntity<User[]> response = restTemplate.getForEntity(
                getBaseUrl() + "/api/users",
                User[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().length).isEqualTo(1);
        assertThat(response.getBody()[0].getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    void testGetUserById_Exists() {
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username("getbyid_" + UUID.randomUUID().toString().substring(0, 8))
                .email(generateUniqueEmail("getbyid"))
                .firstName("Get")
                .lastName("ById")
                .isActive(true)
                .build();
        userRepository.save(user);

        ResponseEntity<User> response = restTemplate.getForEntity(
                getBaseUrl() + "/api/users/" + user.getId(),
                User.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    void testGetUserById_NotFound() {
        ResponseEntity<User> response = restTemplate.getForEntity(
                getBaseUrl() + "/api/users/" + UUID.randomUUID().toString(),
                User.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testGetUserByEmail_Exists() {
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username("getbyemail_" + UUID.randomUUID().toString().substring(0, 8))
                .email(generateUniqueEmail("getbyemail"))
                .firstName("Get")
                .lastName("ByEmail")
                .isActive(true)
                .build();
        userRepository.save(user);

        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/api/users/email/" + user.getEmail(),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("email")).isEqualTo(user.getEmail());
        assertThat(response.getBody().get("firstName")).isEqualTo(user.getFirstName());
        assertThat(response.getBody().get("lastName")).isEqualTo(user.getLastName());
    }

    @Test
    void testGetUserByEmail_NotFound() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/api/users/email/nonexistent@example.com",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
