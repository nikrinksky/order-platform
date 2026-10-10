package com.orderplatform.auth.config;

import com.orderplatform.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private AdminProperties adminProperties;

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminBootstrap adminBootstrap;

    @Test
    void run_SkipsWhenNotConfigured() {
        when(adminProperties.isConfigured()).thenReturn(false);

        adminBootstrap.run(mock(ApplicationArguments.class));

        verifyNoInteractions(userService);
    }

    @Test
    void run_CreatesAdminWhenConfigured() {
        when(adminProperties.isConfigured()).thenReturn(true);
        when(adminProperties.getEmail()).thenReturn("admin@example.com");
        when(adminProperties.getPassword()).thenReturn("secret123");
        when(adminProperties.getFirstName()).thenReturn("Admin");
        when(adminProperties.getLastName()).thenReturn("User");

        adminBootstrap.run(mock(ApplicationArguments.class));

        verify(userService).ensureAdmin("admin@example.com", "secret123", "Admin", "User");
    }
}
