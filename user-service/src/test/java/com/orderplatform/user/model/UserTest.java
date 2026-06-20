package com.orderplatform.user.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void testNoArgsConstructor() {
        User user = new User();
        assertNotNull(user);
    }

    @Test
    void testBuilder() {
        LocalDateTime now = LocalDateTime.now();
        Set<Role> roles = new HashSet<>();
        roles.add(Role.ROLE_USER);

        User user = User.builder()
                .id("test-id")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(roles)
                .isActive(true)
                .createdAt(now)
                .build();

        assertNotNull(user);
        assertEquals("test-id", user.getId());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("Test", user.getFirstName());
        assertEquals("User", user.getLastName());
        assertEquals(1, user.getRoles().size());
        assertTrue(user.isActive());
        assertEquals(now, user.getCreatedAt());
    }

    @Test
    void testGettersAndSetters() {
        User user = new User();
        LocalDateTime now = LocalDateTime.now();
        Set<Role> roles = new HashSet<>();
        roles.add(Role.ROLE_USER);

        user.setId("test-id");
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRoles(roles);
        user.setActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        assertEquals("test-id", user.getId());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("Test", user.getFirstName());
        assertEquals("User", user.getLastName());
        assertEquals(1, user.getRoles().size());
        assertTrue(user.isActive());
        assertEquals(now, user.getCreatedAt());
        assertEquals(now, user.getUpdatedAt());
    }

    @Test
    void testWithEmptyRoles() {
        User user = User.builder()
                .id("test-id")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(new HashSet<>())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        assertNotNull(user);
        assertTrue(user.getRoles().isEmpty());
    }

    @Test
    void testWithNullValues() {
        User user = User.builder()
                .id("test-id")
                .email("test@example.com")
                .firstName(null)
                .lastName(null)
                .roles(null)
                .isActive(false)
                .createdAt(null)
                .updatedAt(null)
                .build();

        assertNotNull(user);
        assertNull(user.getFirstName());
        assertNull(user.getLastName());
        assertNull(user.getRoles());
        assertFalse(user.isActive());
        assertNull(user.getCreatedAt());
        assertNull(user.getUpdatedAt());
    }

    @Test
    void testMultipleRoles() {
        Set<Role> roles = new HashSet<>();
        roles.add(Role.ROLE_USER);
        roles.add(Role.ROLE_MANAGER);
        roles.add(Role.ROLE_ADMIN);

        User user = User.builder()
                .id("test-id")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(roles)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        assertEquals(3, user.getRoles().size());
        assertTrue(user.getRoles().contains(Role.ROLE_USER));
        assertTrue(user.getRoles().contains(Role.ROLE_MANAGER));
        assertTrue(user.getRoles().contains(Role.ROLE_ADMIN));
    }
}
