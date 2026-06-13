package com.orderplatform.user.integration;

import com.orderplatform.user.model.User;
import com.orderplatform.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-integration-tests.yml")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserIntegrationTest {

    @LocalServerPort
    private int port;

    private static final String POSTGRES_HOST = "host.docker.internal";
    private static final int POSTGRES_PORT = 5432;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                String.format("jdbc:postgresql://%s:%d/orderplatform", POSTGRES_HOST, POSTGRES_PORT));
        registry.add("spring.datasource.username", () -> "platform");
        registry.add("spring.datasource.password", () -> "dev123");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

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
    @Order(1)
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
    @Order(2)
    void testGetAllUsers_WithUsers() {
        // Создаем тестового пользователя напрямую в БД
        User user = User.builder()
                .id(UUID.randomUUID().toString())
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
    @Order(3)
    void testGetUserById_Exists() {
        User user = User.builder()
                .id(UUID.randomUUID().toString())
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
    @Order(4)
    void testGetUserById_NotFound() {
        ResponseEntity<User> response = restTemplate.getForEntity(
                getBaseUrl() + "/api/users/" + UUID.randomUUID().toString(),
                User.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(5)
    void testGetUserByEmail_Exists() {
        User user = User.builder()
                .id(UUID.randomUUID().toString())
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
    @Order(6)
    void testGetUserByEmail_NotFound() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/api/users/email/nonexistent@example.com",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
