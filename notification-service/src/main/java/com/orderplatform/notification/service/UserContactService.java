package com.orderplatform.notification.service;

import com.orderplatform.events.user.UserCreatedEvent;
import com.orderplatform.events.user.UserUpdatedEvent;
import com.orderplatform.notification.model.UserContact;
import com.orderplatform.notification.repository.UserContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Maintains the user id to email projection that notifications are addressed with.
 *
 * <p>The order events carry only a user id, so the address has to come from somewhere. Reading it
 * from the user events keeps the flow asynchronous: a call into user-service would make every
 * notification wait on another service being up.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserContactService {

    private final UserContactRepository userContactRepository;

    public void onUserCreated(UserCreatedEvent event) {
        upsert(event.getId(), event.getEmail());
    }

    public void onUserUpdated(UserUpdatedEvent event) {
        upsert(event.getId(), event.getEmail());
    }

    /**
     * Stores the current address for a user.
     *
     * <p>An upsert rather than an insert with a duplicate check: the user id is the document key,
     * so writing twice with the same address is a no-op and an address change simply overwrites
     * the old one. De-duplicating by event id, as the notification flow does, would be wrong here
     * - it would drop exactly the update that carries a new address.
     */
    private void upsert(CharSequence userId, CharSequence email) {
        String id = text(userId);
        if (id == null || id.isBlank()) {
            log.warn("Ignoring user event without an id");
            return;
        }
        userContactRepository.save(UserContact.builder()
                .userId(id)
                .email(text(email))
                .build());
        log.info("Recorded contact for user {}", id);
    }

    /** Avro strings arrive as {@link CharSequence}, not {@link String}. */
    private static String text(CharSequence value) {
        return value == null ? null : value.toString();
    }
}
