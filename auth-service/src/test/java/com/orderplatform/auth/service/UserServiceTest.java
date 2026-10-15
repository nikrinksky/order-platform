package com.orderplatform.auth.service;

import com.orderplatform.auth.dto.RegisterRequest;
import com.orderplatform.auth.dto.UserDto;
import com.orderplatform.auth.model.Role;
import com.orderplatform.auth.model.User;
import com.orderplatform.auth.repository.UserRepository;
import com.orderplatform.events.user.UserCreatedEvent;
import com.orderplatform.events.user.UserUpdatedEvent;
import com.orderplatform.outbox.OutboxService;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private UserService userService;

    private RegisterRequest request;
    private User user;
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        request = RegisterRequest.builder()
                .email("test@example.com")
                .password("password123")
                .firstName("Test")
                .lastName("User")
                .build();

        user = User.builder()
                .id("123")
                .email("test@example.com")
                .password("encoded")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of(Role.ROLE_USER))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userDto = UserDto.builder()
                .id("123")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();
    }

    @Test
    void testRegister_Success() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(any(User.class))).thenReturn(userDto);

        UserDto result = userService.register(request);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(outboxService).append(eq("user.created"), eq("123"), eq("123:CREATED"),
                any(UserCreatedEvent.class));
    }

    /**
     * The event must carry the id user-service stores, otherwise the projection ends up with a
     * user it cannot match to anything.
     */
    @Test
    void testRegister_EventCarriesTheSavedUserData() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(any(User.class))).thenReturn(userDto);

        userService.register(request);

        ArgumentCaptor<SpecificRecordBase> captor = ArgumentCaptor.forClass(SpecificRecordBase.class);
        verify(outboxService).append(eq("user.created"), eq("123"), eq("123:CREATED"), captor.capture());
        UserCreatedEvent event = (UserCreatedEvent) captor.getValue();
        assertThat(event.getId().toString()).isEqualTo("123");
        assertThat(event.getEmail().toString()).isEqualTo("test@example.com");
        assertThat(event.getRoles()).containsExactly("ROLE_USER");
        assertThat(event.getIsActive()).isTrue();
    }

    @Test
    void testRegister_DuplicateEmail_ThrowsException() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void testUpdateUser_Success() {
        when(userRepository.findById("123")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(any(User.class))).thenReturn(userDto);

        UserDto result = userService.updateUser("123", request);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(outboxService).append(eq("user.updated"), eq("123"), eq("123:UPDATED"),
                any(UserUpdatedEvent.class));
    }

    @Test
    void testUpdateUser_NotFound_ThrowsException() {
        when(userRepository.findById("999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser("999", request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void testRegister_DoesNotPublishWithoutGoingThroughTheOutbox() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(any(User.class))).thenReturn(userDto);

        userService.register(request);

        // Every event goes through the outbox: a direct send would lose it on a broker outage
        verify(outboxService, never()).append(anyString(), anyString(), any(SpecificRecordBase.class));
    }

    @Test
    void testUpdateRoles_Success() {
        when(userRepository.findById("123")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(any(User.class))).thenReturn(userDto);

        UserDto result = userService.updateRoles("123", Set.of(Role.ROLE_ADMIN, Role.ROLE_MANAGER));

        assertThat(result).isNotNull();
        verify(userRepository).save(any(User.class));
        verify(outboxService).append(eq("user.updated"), eq("123"), eq("123:UPDATED"),
                any(UserUpdatedEvent.class));
    }

    @Test
    void testUpdateRoles_UserNotFound_ThrowsException() {
        when(userRepository.findById("999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateRoles("999", Set.of(Role.ROLE_USER)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void testEnsureAdmin_CreatesNewAdminWithAdminRole() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        // JPA assigns the id on persist; the mock stands in for that so the event key is non-null.
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User toSave = invocation.getArgument(0);
            if (toSave.getId() == null) {
                toSave.setId("generated-id");
            }
            return toSave;
        });

        userService.ensureAdmin("admin@example.com", "secret123", "Admin", "User");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getPassword()).isEqualTo("encoded");
        assertThat(saved.getRoles()).containsExactly(Role.ROLE_ADMIN);
        verify(outboxService).append(eq("user.created"), eq("generated-id"),
                eq("generated-id:CREATED"), any(UserCreatedEvent.class));
    }

    @Test
    void testEnsureAdmin_ExistingAdmin_IsNoOp() {
        User existingAdmin = User.builder()
                .id("123")
                .email("admin@example.com")
                .roles(Set.of(Role.ROLE_ADMIN))
                .build();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existingAdmin));

        userService.ensureAdmin("admin@example.com", "secret123", "Admin", "User");

        verify(userRepository, never()).save(any(User.class));
        verify(outboxService, never()).append(anyString(), anyString(), anyString(), any());
    }

    @Test
    void testEnsureAdmin_ExistingUserWithoutAdminRole_GrantsRole() {
        User existingUser = User.builder()
                .id("123")
                .email("admin@example.com")
                .roles(new HashSet<>(Set.of(Role.ROLE_USER)))
                .build();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.ensureAdmin("admin@example.com", "secret123", "Admin", "User");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRoles()).contains(Role.ROLE_ADMIN, Role.ROLE_USER);
        verify(outboxService).append(eq("user.updated"), eq("123"), eq("123:UPDATED"),
                any(UserUpdatedEvent.class));
    }
}