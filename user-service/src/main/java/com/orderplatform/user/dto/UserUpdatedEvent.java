package com.orderplatform.user.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdatedEvent {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;
    private boolean isActive;
    private LocalDateTime updatedAt;
}
