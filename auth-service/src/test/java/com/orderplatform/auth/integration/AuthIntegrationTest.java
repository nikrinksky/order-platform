package com.orderplatform.auth.integration;

import com.orderplatform.auth.AbstractIntegrationTest;
import com.orderplatform.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
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
    void testRegister() {
        String uniqueEmail = generateUniqueEmail("test");
        Map<String, String> request = Map.of(
                "email", uniqueEmail,
                "password", "password123",
                "firstName", "Test",
                "lastName", "User"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/auth/register",
                new HttpEntity<>(request, getJsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("email");
        assertThat(response.getBody().get("email")).isEqualTo(uniqueEmail);
    }

    @Test
    void testLogin() {
        String uniqueEmail = generateUniqueEmail("login");

        registerUser(uniqueEmail, "password123", "Login", "User");

        Map<String, String> loginRequest = Map.of(
                "email", uniqueEmail,
                "password", "password123"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/auth/login",
                new HttpEntity<>(loginRequest, getJsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("accessToken");
        assertThat(response.getBody()).containsKey("refreshToken");
    }

    @Test
    void testRefreshToken() {
        String uniqueEmail = generateUniqueEmail("refresh");

        registerUser(uniqueEmail, "password123", "Refresh", "User");

        Map<String, String> loginRequest = Map.of(
                "email", uniqueEmail,
                "password", "password123"
        );

        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/auth/login",
                new HttpEntity<>(loginRequest, getJsonHeaders()),
                Map.class
        );

        String oldRefreshToken = (String) loginResponse.getBody().get("refreshToken");
        assertThat(oldRefreshToken).isNotNull();

        HttpHeaders refreshHeaders = getJsonHeaders();
        refreshHeaders.set("Authorization", "Bearer " + oldRefreshToken);

        ResponseEntity<Map> refreshResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/auth/refresh",
                new HttpEntity<>(null, refreshHeaders),
                Map.class
        );

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).containsKey("accessToken");
        assertThat(refreshResponse.getBody()).containsKey("refreshToken");

        String newAccessToken = (String) refreshResponse.getBody().get("accessToken");

        // Проверяем, что новый access token работает
        HttpHeaders meHeaders = getJsonHeaders();
        meHeaders.set("Authorization", "Bearer " + newAccessToken);

        ResponseEntity<Map> meResponse = restTemplate.exchange(
                getBaseUrl() + "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(null, meHeaders),
                Map.class
        );

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody().get("email")).isEqualTo(uniqueEmail);
    }

    @Test
    void testLogout() {
        String uniqueEmail = generateUniqueEmail("logout");

        registerUser(uniqueEmail, "password123", "Logout", "User");

        Map<String, String> loginRequest = Map.of(
                "email", uniqueEmail,
                "password", "password123"
        );

        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/auth/login",
                new HttpEntity<>(loginRequest, getJsonHeaders()),
                Map.class
        );

        String accessToken = (String) loginResponse.getBody().get("accessToken");

        // Проверяем, что access token работает
        HttpHeaders meHeaders = getJsonHeaders();
        meHeaders.set("Authorization", "Bearer " + accessToken);

        ResponseEntity<Map> meResponseBefore = restTemplate.exchange(
                getBaseUrl() + "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(null, meHeaders),
                Map.class
        );
        assertThat(meResponseBefore.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Logout
        HttpHeaders logoutHeaders = getJsonHeaders();
        logoutHeaders.set("Authorization", "Bearer " + accessToken);

        ResponseEntity<Map> logoutResponse = restTemplate.exchange(
                getBaseUrl() + "/api/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(null, logoutHeaders),
                Map.class
        );
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Проверяем, что access token больше не работает
        ResponseEntity<Map> meResponseAfter = restTemplate.exchange(
                getBaseUrl() + "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(null, meHeaders),
                Map.class
        );
        assertThat(meResponseAfter.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testRegisterDuplicateEmail() {
        String uniqueEmail = generateUniqueEmail("duplicate");

        registerUser(uniqueEmail, "password123", "Duplicate", "User");

        Map<String, String> request = Map.of(
                "email", uniqueEmail,
                "password", "password456",
                "firstName", "Another",
                "lastName", "User"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/auth/register",
                new HttpEntity<>(request, getJsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void registerUser(String email, String password, String firstName, String lastName) {
        Map<String, String> request = Map.of(
                "email", email,
                "password", password,
                "firstName", firstName,
                "lastName", lastName
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/auth/register",
                new HttpEntity<>(request, getJsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}