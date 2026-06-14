package com.orderplatform.user.repository;

import com.orderplatform.user.model.Role;
import com.orderplatform.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
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
                .build();
        entityManager.persistAndFlush(testUser);
    }

    @Test
    void testFindByEmail_ExistingUser() {
        // when
        var result = userRepository.findByEmail("test@example.com");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");
        assertThat(result.get().getFirstName()).isEqualTo("Test");
    }

    @Test
    void testFindByEmail_NonExistingUser() {
        // when
        var result = userRepository.findByEmail("nonexistent@example.com");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void testExistsByEmail_ExistingUser() {
        // when
        boolean exists = userRepository.existsByEmail("test@example.com");

        // then
        assertThat(exists).isTrue();
    }

    @Test
    void testExistsByEmail_NonExistingUser() {
        // when
        boolean exists = userRepository.existsByEmail("nonexistent@example.com");

        // then
        assertThat(exists).isFalse();
    }

    @Test
    void testSaveUser() {
        // given
        User newUser = User.builder()
                .id("new-user-456")
                .email("new@example.com")
                .firstName("New")
                .lastName("User")
                .roles(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN))
                .isActive(true)
                .build();

        // when
        User saved = userRepository.save(newUser);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getRoles()).hasSize(2);
    }

    @Test
    void testDeleteUser() {
        // when
        userRepository.deleteById("test-user-123");

        // then
        var result = userRepository.findById("test-user-123");
        assertThat(result).isEmpty();
    }
}
