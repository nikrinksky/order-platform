package com.orderplatform.user.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserCreatedEventTest {

    @Test
    void testBuilder() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When
        UserCreatedEvent event = UserCreatedEvent.builder()
                .id("test-id")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of("ROLE_USER", "ROLE_MANAGER"))
                .isActive(true)
                .createdAt(now)
                .build();

        // Then
        assertNotNull(event);
        assertEquals("test-id", event.getId());
        assertEquals("test@example.com", event.getEmail());
        assertEquals("Test", event.getFirstName());
        assertEquals("User", event.getLastName());
        assertEquals(2, event.getRoles().size());
        assertTrue(event.isActive());
        assertEquals(now, event.getCreatedAt());
    }

    @Test
    void testGettersAndSetters() {
        // Given
        UserCreatedEvent event = new UserCreatedEvent();
        LocalDateTime now = LocalDateTime.now();

        // When
        event.setId("test-id");
        event.setEmail("test@example.com");
        event.setFirstName("Test");
        event.setLastName("User");
        event.setRoles(Set.of("ROLE_USER"));
        event.setActive(false);
        event.setCreatedAt(now);

        // Then
        assertEquals("test-id", event.getId());
        assertEquals("test@example.com", event.getEmail());
        assertEquals("Test", event.getFirstName());
        assertEquals("User", event.getLastName());
        assertEquals(1, event.getRoles().size());
        assertFalse(event.isActive());
        assertEquals(now, event.getCreatedAt());
    }

    @Test
    void testNoArgsConstructor() {
        // When
        UserCreatedEvent event = new UserCreatedEvent();

        // Then
        assertNotNull(event);
    }

    @Test
    void testAllArgsConstructor() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When
        UserCreatedEvent event = new UserCreatedEvent(
                "test-id",
                "test@example.com",
                "Test",
                "User",
                Set.of("ROLE_USER"),
                true,
                now
        );

        // Then
        assertNotNull(event);
        assertEquals("test-id", event.getId());
        assertEquals("test@example.com", event.getEmail());
        assertEquals("Test", event.getFirstName());
        assertEquals("User", event.getLastName());
        assertEquals(1, event.getRoles().size());
        assertTrue(event.isActive());
        assertEquals(now, event.getCreatedAt());
    }

    @Test
    void testWithEmptyRoles() {
        // When
        UserCreatedEvent event = UserCreatedEvent.builder()
                .id("test-id")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        // Then
        assertNotNull(event);
        assertTrue(event.getRoles().isEmpty());
    }

    @Test
    void testWithNullFields() {
        // When
        UserCreatedEvent event = UserCreatedEvent.builder()
                .id("test-id")
                .email("test@example.com")
                .firstName(null)
                .lastName(null)
                .roles(null)
                .isActive(false)
                .createdAt(null)
                .build();

        // Then
        assertNotNull(event);
        assertNull(event.getFirstName());
        assertNull(event.getLastName());
        assertNull(event.getRoles());
        assertFalse(event.isActive());
        assertNull(event.getCreatedAt());
    }
}
