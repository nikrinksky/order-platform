package com.orderplatform.auth.service;

import com.orderplatform.auth.dto.RegisterRequest;
import com.orderplatform.auth.dto.UserCreatedEvent;
import com.orderplatform.auth.dto.UserDto;
import com.orderplatform.auth.model.Role;
import com.orderplatform.auth.model.User;
import com.orderplatform.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.kafka.common.requests.DeleteAclsResponse.log;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UserDto register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("User with email " + request.getEmail() + " already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .roles(Set.of(Role.ROLE_USER))
                .isActive(true)
//                .isEmailVerified(false)
                .build();

        User savedUser = userRepository.save(user);


        // Отправка события в Kafka
        sendUserCreatedEvent(savedUser);

        return userMapper.toDto(savedUser);
    }

    private void sendUserCreatedEvent(User user) {
        try {
            UserCreatedEvent event = UserCreatedEvent.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .roles(user.getRoles().stream()
                            .map(Enum::name)
                            .collect(Collectors.toSet()))
                    .isActive(user.isActive())
                    .createdAt(user.getCreatedAt())
                    .build();

            kafkaTemplate.send("user.created", user.getId(), event);
            log.info("UserCreatedEvent sent for user: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send UserCreatedEvent: {}", e.getMessage());
        }
    }
}