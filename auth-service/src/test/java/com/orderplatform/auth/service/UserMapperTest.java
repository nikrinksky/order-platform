package com.orderplatform.auth.service;

import com.orderplatform.auth.dto.UserDto;
import com.orderplatform.auth.model.Role;
import com.orderplatform.auth.model.User;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    void toDto_shouldMapUserFields() {
        User user = User.builder()
                .id("1")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN))
                .isActive(true)
                .build();

        UserDto dto = mapper.toDto(user);

        assertNotNull(dto);
        assertEquals("1", dto.getId());
        assertEquals("test@example.com", dto.getEmail());
        assertEquals("Test", dto.getFirstName());
        assertEquals("User", dto.getLastName());
        assertTrue(dto.getRoles().contains("ROLE_USER"));
        assertTrue(dto.getRoles().contains("ROLE_ADMIN"));
        assertTrue(dto.isActive());
    }

    @Test
    void toDto_shouldReturnNullForNullUser() {
        assertNull(mapper.toDto(null));
    }
}