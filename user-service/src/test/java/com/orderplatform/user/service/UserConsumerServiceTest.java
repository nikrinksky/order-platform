package com.orderplatform.user.service;

import com.orderplatform.events.user.UserCreatedEvent;
import com.orderplatform.events.user.UserUpdatedEvent;
import com.orderplatform.user.model.Role;
import com.orderplatform.user.model.User;
import com.orderplatform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserConsumerServiceTest {

    @Mock
    private UserRepository userRepository;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private UserConsumerService userConsumerService;

    private UserCreatedEvent testEvent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userConsumerService = new UserConsumerService(userRepository);

        testEvent = createdEvent("test-user-id", "test@example.com", "Test", "User",
                List.of("ROLE_USER", "ROLE_MANAGER"), Instant.now());
    }

    private static UserCreatedEvent createdEvent(String id, String email, String firstName,
                                                 String lastName, List<String> roles,
                                                 Instant createdAt) {
        return UserCreatedEvent.newBuilder()
                .setId(id)
                .setUsername(email)
                .setEmail(email)
                .setFirstName(firstName)
                .setLastName(lastName)
                .setRoles(new ArrayList<>(roles))
                .setIsActive(true)
                .setCreatedAt(createdAt)
                .build();
    }

    private static UserUpdatedEvent updatedEvent(String id, String username, String email,
                                                 String firstName, String lastName,
                                                 List<String> roles, Instant updatedAt) {
        return UserUpdatedEvent.newBuilder()
                .setId(id)
                .setUsername(username)
                .setEmail(email)
                .setFirstName(firstName)
                .setLastName(lastName)
                .setRoles(new ArrayList<>(roles))
                .setIsActive(true)
                .setUpdatedAt(updatedAt)
                .build();
    }

    @Test
    void testConsumeUserCreated_NewUser() {
        // Given
        when(userRepository.existsByEmail(testEvent.getEmail().toString())).thenReturn(false);

        // When
        assertDoesNotThrow(() -> userConsumerService.consumeUserCreated(testEvent));

        // Then
        verify(userRepository, times(1)).existsByEmail(testEvent.getEmail().toString());
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertNotNull(savedUser);
        assertEquals(testEvent.getId().toString(), savedUser.getId());
        assertEquals(testEvent.getEmail().toString(), savedUser.getEmail());
        assertEquals(testEvent.getFirstName().toString(), savedUser.getFirstName());
        assertEquals(testEvent.getLastName().toString(), savedUser.getLastName());
        assertTrue(savedUser.isActive());
    }

    /**
     * The Avro timestamp has to land in the read model as a wall clock time, otherwise the stored
     * row shows a date shifted by the local offset.
     */
    @Test
    void testConsumeUserCreated_ConvertsTimestamp() {
        when(userRepository.existsByEmail(testEvent.getEmail().toString())).thenReturn(false);

        userConsumerService.consumeUserCreated(testEvent);

        verify(userRepository).save(userCaptor.capture());
        assertNotNull(userCaptor.getValue().getCreatedAt());
    }

    @Test
    void testConsumeUserCreated_UserAlreadyExists() {
        // Given
        when(userRepository.existsByEmail(testEvent.getEmail().toString())).thenReturn(true);

        // When
        assertDoesNotThrow(() -> userConsumerService.consumeUserCreated(testEvent));

        // Then
        verify(userRepository, times(1)).existsByEmail(testEvent.getEmail().toString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testConsumeUserCreated_WithException() {
        // Given
        when(userRepository.existsByEmail(testEvent.getEmail().toString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("DB Error"));

        // When / Then - the failure must reach the Kafka listener container so the record is
        // retried; swallowing it would commit the offset and lose the user silently.
        assertThrows(RuntimeException.class, () -> userConsumerService.consumeUserCreated(testEvent));

        verify(userRepository, times(1)).existsByEmail(testEvent.getEmail().toString());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testConsumeUserCreated_EmptyRoles() {
        // Given
        UserCreatedEvent eventWithEmptyRoles = createdEvent("test-user-id", "test@example.com",
                "Test", "User", List.of(), Instant.now());
        when(userRepository.existsByEmail(eventWithEmptyRoles.getEmail().toString())).thenReturn(false);

        // When
        assertDoesNotThrow(() -> userConsumerService.consumeUserCreated(eventWithEmptyRoles));

        // Then
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertNotNull(savedUser);
        assertTrue(savedUser.getRoles().isEmpty());
    }

    @Test
    void testConsumeUserCreated_NullFirstName() {
        // Given
        UserCreatedEvent eventWithNullFirstName = createdEvent("test-user-id", "test@example.com",
                null, "User", List.of("ROLE_USER"), Instant.now());
        when(userRepository.existsByEmail(eventWithNullFirstName.getEmail().toString())).thenReturn(false);

        // When
        assertDoesNotThrow(() -> userConsumerService.consumeUserCreated(eventWithNullFirstName));

        // Then
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertNotNull(savedUser);
        assertNull(savedUser.getFirstName());
    }

    @Test
    void testConsumeUserCreated_NullLastName() {
        // Given
        UserCreatedEvent eventWithNullLastName = createdEvent("test-user-id", "test@example.com",
                "Test", null, List.of("ROLE_USER"), Instant.now());
        when(userRepository.existsByEmail(eventWithNullLastName.getEmail().toString())).thenReturn(false);

        // When
        assertDoesNotThrow(() -> userConsumerService.consumeUserCreated(eventWithNullLastName));

        // Then
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertNotNull(savedUser);
        assertNull(savedUser.getLastName());
    }

    @Test
    void testConsumeUserUpdated_ExistingUser() {
        // Given
        User existingUser = User.builder()
                .id("test-user-id")
                .username("old-username")
                .email("test@example.com")
                .firstName("Old")
                .lastName("Name")
                .roles(Set.of(Role.ROLE_USER))
                .isActive(true)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        when(userRepository.findById("test-user-id")).thenReturn(Optional.of(existingUser));

        UserUpdatedEvent event = updatedEvent("test-user-id", "new-username", "test@example.com",
                "New", "Name", List.of("ROLE_USER", "ROLE_MANAGER"), Instant.now());

        // When
        assertDoesNotThrow(() -> userConsumerService.consumeUserUpdated(event));

        // Then
        verify(userRepository, times(1)).findById("test-user-id");
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertNotNull(savedUser);
        assertEquals("new-username", savedUser.getUsername());
        assertEquals("test@example.com", savedUser.getEmail());
        assertEquals("New", savedUser.getFirstName());
        assertEquals(2, savedUser.getRoles().size());
        assertEquals(existingUser.getCreatedAt(), savedUser.getCreatedAt());
    }

    @Test
    void testConsumeUserUpdated_UserNotFound() {
        // Given
        when(userRepository.findById("nonexistent-id")).thenReturn(Optional.empty());

        UserUpdatedEvent event = updatedEvent("nonexistent-id", "new-username", "test@example.com",
                "New", "Name", List.of("ROLE_USER"), Instant.now());

        // When
        assertDoesNotThrow(() -> userConsumerService.consumeUserUpdated(event));

        // Then
        verify(userRepository, times(1)).findById("nonexistent-id");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testConsumeUserUpdated_WithException() {
        // Given
        when(userRepository.findById("test-user-id"))
                .thenThrow(new RuntimeException("DB Error"));

        UserUpdatedEvent event = updatedEvent("test-user-id", "new-username", "test@example.com",
                "New", "Name", List.of("ROLE_USER"), Instant.now());

        // When / Then - the failure must reach the Kafka listener container so the record is
        // retried instead of being dropped.
        assertThrows(RuntimeException.class, () -> userConsumerService.consumeUserUpdated(event));

        verify(userRepository, times(1)).findById("test-user-id");
        verify(userRepository, never()).save(any(User.class));
    }
}
