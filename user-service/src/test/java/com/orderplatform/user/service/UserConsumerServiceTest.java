package com.orderplatform.user.service;

import com.orderplatform.user.UserServiceApplication;
import com.orderplatform.user.dto.UserCreatedEvent;
import com.orderplatform.user.model.Role;
import com.orderplatform.user.model.User;
import com.orderplatform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    properties = {
        "spring.kafka.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    },
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("integration-tests")
@ContextConfiguration(classes = {UserServiceApplication.class, UserConsumerService.class})
class UserConsumerServiceTest {

    @Autowired
    private UserConsumerService userConsumerService;

    @Autowired
    private UserRepository userRepository;

    private UserCreatedEvent testEvent;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        testEvent = UserCreatedEvent.builder()
                .id("event-123")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of("ROLE_USER"))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testConsumeUserCreated_NewUser() {
        // when
        userConsumerService.consumeUserCreated(testEvent);

        // then
        var savedUser = userRepository.findByEmail("test@example.com");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.get().getFirstName()).isEqualTo("Test");
        assertThat(savedUser.get().getRoles()).hasSize(1);
    }

    @Test
    void testConsumeUserCreated_DuplicateUser() {
        // given - создаём пользователя в БД
        User existingUser = User.builder()
                .id("existing-123")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of(Role.ROLE_USER))
                .isActive(true)
                .build();
        userRepository.save(existingUser);

        // when
        userConsumerService.consumeUserCreated(testEvent);

        // then - пользователь не должен быть перезаписан
        var savedUser = userRepository.findByEmail("test@example.com");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getId()).isEqualTo("existing-123");
    }

    @Test
    void testConsumeUserCreated_MultipleRoles() {
        // given - событие с несколькими ролями
        UserCreatedEvent eventWithMultipleRoles = UserCreatedEvent.builder()
                .id("event-456")
                .email("admin@example.com")
                .firstName("Admin")
                .lastName("User")
                .roles(Set.of("ROLE_USER", "ROLE_ADMIN"))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        // when
        userConsumerService.consumeUserCreated(eventWithMultipleRoles);

        // then
        var savedUser = userRepository.findByEmail("admin@example.com");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getRoles()).hasSize(2);
        assertThat(savedUser.get().getRoles()).contains(Role.ROLE_USER, Role.ROLE_ADMIN);
    }
}
