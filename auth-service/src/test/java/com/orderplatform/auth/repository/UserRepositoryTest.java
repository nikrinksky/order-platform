package com.orderplatform.auth.repository;

import com.orderplatform.auth.model.Role;
import com.orderplatform.auth.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveUser() {
        User user = User.builder()
                .email("test@example.com")
                .password("encodedPassword")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of(Role.ROLE_USER))
                .isActive(true)
                .build();

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void shouldFindByEmail() {
        User user = User.builder()
                .email("find@example.com")
                .password("password")
                .firstName("Find")
                .lastName("Me")
                .roles(Set.of(Role.ROLE_USER))
                .build();

        userRepository.save(user);

        var found = userRepository.findByEmail("find@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("find@example.com");
    }

    @Test
    void shouldCheckEmailExists() {
        User user = User.builder()
                .email("exists@example.com")
                .password("password")
                .firstName("Exists")
                .lastName("User")
                .roles(Set.of(Role.ROLE_USER))
                .build();

        userRepository.save(user);

        boolean exists = userRepository.existsByEmail("exists@example.com");
        assertThat(exists).isTrue();

        boolean notExists = userRepository.existsByEmail("nonexistent@example.com");
        assertThat(notExists).isFalse();
    }
}