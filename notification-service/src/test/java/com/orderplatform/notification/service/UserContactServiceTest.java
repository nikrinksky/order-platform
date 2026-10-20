package com.orderplatform.notification.service;

import com.orderplatform.events.user.UserCreatedEvent;
import com.orderplatform.events.user.UserUpdatedEvent;
import com.orderplatform.notification.model.UserContact;
import com.orderplatform.notification.repository.UserContactRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserContactServiceTest {

    @Mock
    private UserContactRepository userContactRepository;

    @InjectMocks
    private UserContactService userContactService;

    @Test
    void onUserCreated_shouldRecordTheAddress() {
        userContactService.onUserCreated(UserCreatedEvent.newBuilder()
                .setId("user-1")
                .setUsername("buyer")
                .setEmail("buyer@example.com")
                .setRoles(List.of("ROLE_USER"))
                .setIsActive(true)
                .build());

        ArgumentCaptor<UserContact> saved = ArgumentCaptor.forClass(UserContact.class);
        verify(userContactRepository).save(saved.capture());
        assertEquals("user-1", saved.getValue().getUserId());
        assertEquals("buyer@example.com", saved.getValue().getEmail());
    }

    /**
     * An address change is exactly what has to be applied, so the write is an upsert keyed by the
     * user id rather than a de-duplicated insert: de-duplicating by event id would drop it.
     */
    @Test
    void onUserUpdated_shouldOverwriteTheAddress() {
        userContactService.onUserUpdated(UserUpdatedEvent.newBuilder()
                .setId("user-1")
                .setUsername("buyer")
                .setEmail("new-address@example.com")
                .setRoles(List.of("ROLE_USER"))
                .setIsActive(true)
                .build());

        ArgumentCaptor<UserContact> saved = ArgumentCaptor.forClass(UserContact.class);
        verify(userContactRepository).save(saved.capture());
        assertEquals("new-address@example.com", saved.getValue().getEmail());
    }

    @Test
    void shouldIgnoreAnEventWithoutAnId() {
        userContactService.onUserCreated(UserCreatedEvent.newBuilder()
                .setId("")
                .setUsername("buyer")
                .setEmail("buyer@example.com")
                .setRoles(List.of("ROLE_USER"))
                .setIsActive(true)
                .build());

        verify(userContactRepository, never()).save(any(UserContact.class));
    }
}
