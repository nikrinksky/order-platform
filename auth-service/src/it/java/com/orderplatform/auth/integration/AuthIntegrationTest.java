package com.orderplatform.auth.integration;

import com.orderplatform.auth.AbstractIntegrationTest;
import com.orderplatform.auth.model.Role;
import com.orderplatform.auth.model.User;
import com.orderplatform.auth.repository.UserRepository;
import com.orderplatform.outbox.OutboxRecord;
import com.orderplatform.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
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

    /**
     * The account and the announcement of it must be written in one transaction: publishing
     * straight to Kafka used to lose the event whenever the broker was down, leaving a user that
     * user-service never learned about.
     */
    @Test
    void testRegister_AppendsUserCreatedEventToOutbox() {
        String uniqueEmail = generateUniqueEmail("outbox");

        registerUser(uniqueEmail, "password123", "Outbox", "User");

        List<OutboxRecord> records = outboxRepository.findAll();
        assertThat(records).hasSize(1);

        OutboxRecord record = records.get(0);
        assertThat(record.getTopic()).isEqualTo("user.created");
        assertThat(record.getEventType()).isEqualTo("UserCreatedEvent");
        assertThat(record.getAggregateId()).isNotBlank();
        assertThat(record.getEventId()).isEqualTo(record.getAggregateId() + ":CREATED");
        assertThat(record.getPayload()).contains(uniqueEmail);
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
        refreshHeaders.set("X-Refresh-Token", "Bearer " + oldRefreshToken);

        ResponseEntity<Map> refreshResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/auth/refresh",
                new HttpEntity<>(refreshHeaders),
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
        String refreshToken = (String) loginResponse.getBody().get("refreshToken");

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
        logoutHeaders.set("X-Refresh-Token", "Bearer " + refreshToken);

        ResponseEntity<Map> logoutResponse = restTemplate.postForEntity(
                getBaseUrl() + "/api/auth/logout",
                new HttpEntity<>(logoutHeaders),
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

    private String loginAndGetAccessToken(String email, String password) {
        Map<String, String> loginRequest = Map.of(
                "email", email,
                "password", password
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/auth/login",
                new HttpEntity<>(loginRequest, getJsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    /**
     * An administrator must be able to promote an account: registration only ever assigns
     * ROLE_USER, so without this endpoint roles can only be changed with SQL.
     */
    @Test
    void testUpdateRoles_AdminCanChangeRoles() {
        String adminEmail = generateUniqueEmail("admin");
        String userEmail = generateUniqueEmail("promote");
        registerUser(adminEmail, "password123", "Admin", "User");
        registerUser(userEmail, "password123", "Promote", "User");

        // Grant the admin role directly; the startup bootstrap itself is covered by unit tests.
        User admin = userRepository.findByEmail(adminEmail).orElseThrow();
        admin.setRoles(Set.of(Role.ROLE_ADMIN));
        userRepository.save(admin);

        String adminToken = loginAndGetAccessToken(adminEmail, "password123");
        User target = userRepository.findByEmail(userEmail).orElseThrow();

        HttpHeaders headers = getJsonHeaders();
        headers.set("Authorization", "Bearer " + adminToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/auth/users/" + target.getId() + "/roles",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("roles", List.of("ROLE_MANAGER")), headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        User updated = userRepository.findByEmail(userEmail).orElseThrow();
        assertThat(updated.getRoles()).containsExactly(Role.ROLE_MANAGER);
    }

    /**
     * The endpoint must not be reachable by a regular user, otherwise anyone could grant
     * themselves ROLE_ADMIN.
     */
    @Test
    void testUpdateRoles_NonAdminIsForbidden() {
        String userEmail = generateUniqueEmail("plain");
        registerUser(userEmail, "password123", "Plain", "User");
        String token = loginAndGetAccessToken(userEmail, "password123");
        User target = userRepository.findByEmail(userEmail).orElseThrow();

        HttpHeaders headers = getJsonHeaders();
        headers.set("Authorization", "Bearer " + token);

        ResponseEntity<Map> response = restTemplate.exchange(
                getBaseUrl() + "/api/auth/users/" + target.getId() + "/roles",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("roles", List.of("ROLE_ADMIN")), headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}