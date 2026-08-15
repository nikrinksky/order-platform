package com.orderplatform.user.service;

import com.orderplatform.user.dto.UserCreatedEvent;
import com.orderplatform.user.dto.UserUpdatedEvent;
import com.orderplatform.user.model.Role;
import com.orderplatform.user.model.User;
import com.orderplatform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
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

        testEvent = UserCreatedEvent.builder()
                .id("test-user-id")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of("ROLE_USER", "ROLE_MANAGER"))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testConsumeUserCreated_NewUser() {
        // Given
        when(userRepository.existsByEmail(testEvent.getEmail())).thenReturn(false);

        // When
        assertDoesNotThrow(() -> userConsumerService.consumeUserCreated(testEvent));

        // Then
        verify(userRepository, times(1)).existsByEmail(testEvent.getEmail());
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertNotNull(savedUser);
        assertEquals(testEvent.getId(), savedUser.getId());
        assertEquals(testEvent.getEmail(), savedUser.getEmail());
        assertEquals(testEvent.getFirstName(), savedUser.getFirstName());
        assertEquals(testEvent.getLastName(), savedUser.getLastName());
        assertEquals(testEvent.isActive(), savedUser.isActive());
    }

    @Test
    void testConsumeUserCreated_UserAlreadyExists() {
        // Given
        when(userRepository.existsByEmail(testEvent.getEmail())).thenReturn(true);

        // When
        assertDoesNotThrow(() -> userConsumerService.consumeUserCreated(testEvent));

        // Then
        verify(userRepository, times(1)).existsByEmail(testEvent.getEmail());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testConsumeUserCreated_WithException() {
        // Given
        when(userRepository.existsByEmail(testEvent.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("DB Error"));

        // When
        assertDoesNotThrow(() -> userConsumerService.consumeUserCreated(testEvent));

        // Then - exception should be caught and logged
        verify(userRepository, times(1)).existsByEmail(testEvent.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testConsumeUserCreated_EmptyRoles() {
        // Given
        UserCreatedEvent eventWithEmptyRoles = UserCreatedEvent.builder()
                .id("test-user-id")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
        when(userRepository.existsByEmail(eventWithEmptyRoles.getEmail())).thenReturn(false);

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
        UserCreatedEvent eventWithNullFirstName = UserCreatedEvent.builder()
                .id("test-user-id")
                .email("test@example.com")
                .firstName(null)
                .lastName("User")
                .roles(Set.of("ROLE_USER"))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
        when(userRepository.existsByEmail(eventWithNullFirstName.getEmail())).thenReturn(false);

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
        UserCreatedEvent eventWithNullLastName = UserCreatedEvent.builder()
                .id("test-user-id")
                .email("test@example.com")
                .firstName("Test")
                .lastName(null)
                .roles(Set.of("ROLE_USER"))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
        when(userRepository.existsByEmail(eventWithNullLastName.getEmail())).thenReturn(false);

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

        UserUpdatedEvent updatedEvent = UserUpdatedEvent.builder()
                .id("test-user-id")
                .username("new-username")
                .email("test@example.com")
                .firstName("New")
                .lastName("Name")
                .roles(Set.of("ROLE_USER", "ROLE_MANAGER"))
                .isActive(true)
                .updatedAt(LocalDateTime.now())
                .build();

        // When
        assertDoesNotThrow(() -> userConsumerService.consumeUserUpdated(updatedEvent));

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

        UserUpdatedEvent updatedEvent = UserUpdatedEvent.builder()
                .id("nonexistent-id")
                .username("new-username")
                .email("test@example.com")
                .firstName("New")
                .lastName("Name")
                .roles(Set.of("ROLE_USER"))
                .isActive(true)
                .updatedAt(LocalDateTime.now())
                .build();

        // When
        assertDoesNotThrow(() -> userConsumerService.consumeUserUpdated(updatedEvent));

        // Then
        verify(userRepository, times(1)).findById("nonexistent-id");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testConsumeUserUpdated_WithException() {
        // Given
        when(userRepository.findById("test-user-id"))
                .thenThrow(new RuntimeException("DB Error"));

        UserUpdatedEvent updatedEvent = UserUpdatedEvent.builder()
                .id("test-user-id")
                .username("new-username")
                .email("test@example.com")
                .firstName("New")
                .lastName("Name")
                .roles(Set.of("ROLE_USER"))
                .isActive(true)
                .updatedAt(LocalDateTime.now())
                .build();

        // When
        assertDoesNotThrow(() -> userConsumerService.consumeUserUpdated(updatedEvent));

        // Then - exception should be caught and logged
        verify(userRepository, times(1)).findById("test-user-id");
        verify(userRepository, never()).save(any(User.class));
    }
}
