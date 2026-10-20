package com.orderplatform.notification.config;

import com.orderplatform.events.user.UserCreatedEvent;
import com.orderplatform.events.user.UserUpdatedEvent;
import com.orderplatform.notification.service.UserContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Keeps the user id to email projection in step with the user events.
 *
 * <p>The offset is acknowledged only after the contact is stored, so a crash in between replays
 * the event. Replaying is harmless: the write is an upsert keyed by the user id.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserContactKafkaListener {

    private final UserContactService userContactService;

    @KafkaListener(topics = "user.created", groupId = "notification-service-group")
    public void onUserCreated(UserCreatedEvent event, Acknowledgment acknowledgment) {
        log.info("Received user.created event for user: {}", event.getId());
        userContactService.onUserCreated(event);
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = "user.updated", groupId = "notification-service-group")
    public void onUserUpdated(UserUpdatedEvent event, Acknowledgment acknowledgment) {
        log.info("Received user.updated event for user: {}", event.getId());
        userContactService.onUserUpdated(event);
        acknowledgment.acknowledge();
    }
}
