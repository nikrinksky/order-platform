/**
 * Mapper for User entity to UserDto conversion.
 * Converts between User entity and UserDto for API responses.
 */
package com.orderplatform.auth.service;

import com.orderplatform.auth.dto.UserDto;
import com.orderplatform.auth.model.Role;
import com.orderplatform.auth.model.User;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Mapper for User entity to UserDto conversion.
 * Converts between User entity and UserDto for API responses.
 */
@Component
public class UserMapper {

    /**
     * Converts a User entity to UserDto.
     *
     * @param user the user entity
     * @return the user DTO
     */
    public UserDto toDto(User user) {
        if (user == null) {
            return null;
        }

        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles().stream()
                        .map(Role::name)
                        .collect(Collectors.toSet()))
                .isActive(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
