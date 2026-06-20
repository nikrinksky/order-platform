package com.orderplatform.user.repository;

import com.orderplatform.user.model.Role;
import com.orderplatform.user.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("test-user-id")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of(Role.ROLE_USER))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void testFindById() {
        // Given
        User savedUser = userRepository.save(testUser);

        // When
        Optional<User> foundUser = userRepository.findById(savedUser.getId());

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo(testUser.getEmail());
        assertThat(foundUser.get().getFirstName()).isEqualTo(testUser.getFirstName());
    }

    @Test
    void testFindAll() {
        // Given
        userRepository.save(testUser);

        User anotherUser = User.builder()
                .id("another-user-id")
                .email("another@example.com")
                .firstName("Another")
                .lastName("User")
                .roles(Set.of(Role.ROLE_USER))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(anotherUser);

        // When
        Iterable<User> users = userRepository.findAll();

        // Then
        assertThat(users).hasSize(2);
    }

    @Test
    void testFindByEmail() {
        // Given
        userRepository.save(testUser);

        // When
        Optional<User> foundUser = userRepository.findByEmail(testUser.getEmail());

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo(testUser.getEmail());
    }

    @Test
    void testFindByEmail_NotFound() {
        // When
        Optional<User> foundUser = userRepository.findByEmail("nonexistent@example.com");

        // Then
        assertThat(foundUser).isEmpty();
    }

    @Test
    void testExistsByEmail() {
        // Given
        userRepository.save(testUser);

        // When
        boolean exists = userRepository.existsByEmail(testUser.getEmail());

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void testExistsByEmail_NotFound() {
        // When
        boolean exists = userRepository.existsByEmail("nonexistent@example.com");

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void testSaveUser() {
        // When
        User savedUser = userRepository.save(testUser);

        // Then
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo(testUser.getEmail());
    }

    @Test
    void testDeleteUser() {
        // Given
        User savedUser = userRepository.save(testUser);

        // When
        userRepository.delete(savedUser);

        // Then
        Optional<User> foundUser = userRepository.findById(savedUser.getId());
        assertThat(foundUser).isEmpty();
    }
}
