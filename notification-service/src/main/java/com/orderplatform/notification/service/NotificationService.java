package com.orderplatform.notification.service;

import com.orderplatform.events.order.OrderCreatedEvent;
import com.orderplatform.events.order.OrderStatusChangedEvent;
import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.model.UserContact;
import com.orderplatform.notification.repository.NotificationRepository;
import com.orderplatform.notification.repository.UserContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserContactRepository userContactRepository;
    private final EmailSender emailSender;

    /**
     * Stores the notification for a created order.
     *
     * @param eventId delivery id of the Kafka record; null when the producer sent no id, in
     *                which case a redelivery would be stored again - there is nothing to match on
     */
    @Transactional
    public void onOrderCreated(OrderCreatedEvent event, String eventId) {
        if (isRedelivery("order.created", eventId)) {
            return;
        }
        log.info("Processing order created event for order: {}", event.getOrderId());

        final Notification notification = Notification.builder()
                .userId(text(event.getUserId()))
                .eventId(eventId)
                .type("ORDER_CREATED")
                .subject("Order Created: " + text(event.getOrderNumber()))
                .body("Your order " + text(event.getOrderNumber()) + " with total amount $" +
                        decimal(event.getTotalAmount()) + " has been created.")
                .channel("EMAIL")
                .sent(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
        log.info("Notification created for order: {}", event.getOrderId());
        deliver(notification);
    }

    @Transactional
    public void onOrderStatusChanged(OrderStatusChangedEvent event, String eventId) {
        if (isRedelivery("order.status-changed", eventId)) {
            return;
        }
        log.info("Processing order status changed event for order: {}", event.getOrderId());

        final Notification notification = Notification.builder()
                .userId(text(event.getUserId()))
                .eventId(eventId)
                .type("ORDER_STATUS_CHANGED")
                .subject("Order Status: " + text(event.getStatus()))
                .body("Your order " + text(event.getOrderNumber()) + " status has been updated to " +
                        text(event.getStatus()) + ".")
                .channel("EMAIL")
                .sent(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
        log.info("Notification created for status change: {}", event.getOrderId());
        deliver(notification);
    }

    /**
     * Sends the notification to its recipient and records that it went out.
     *
     * <p>Sent straight from the consumer rather than handed to a background job: the caller
     * acknowledges the Kafka offset only after this returns, so a failure keeps the event on the
     * topic and the retry redelivers it. Marking the notification sent is the last step - a
     * notification that could not be handed to the mail server stays unsent and is visible as
     * such.
     *
     * <p>When no address is known the notification is stored unsent and logged instead of being
     * dropped: the contact projection may simply not have caught up yet, and the record still has
     * to be readable in the notification history.
     *
     * @param notification the stored notification to deliver
     * @throws RuntimeException when the mail server rejects the message, so the delivery is
     *                          retried rather than silently reported as done
     */
    private void deliver(Notification notification) {
        String email = addressOf(notification.getUserId());
        if (email == null) {
            log.warn("No email known for user {}, notification {} stays unsent",
                    notification.getUserId(), notification.getId());
            return;
        }

        emailSender.send(email, notification.getSubject(), notification.getBody());

        notification.setSent(true);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
        log.info("Notification {} sent to {}", notification.getId(), email);
    }

    private String addressOf(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userContactRepository.findById(userId)
                .map(UserContact::getEmail)
                .filter(email -> !email.isBlank())
                .orElse(null);
    }

    /**
     * Tells whether this delivery was already processed.
     *
     * <p>Delivery is at-least-once, so a redelivery has to be recognised and dropped. The event
     * type is part of the message on purpose: one shared call site keeps the two consumers from
     * producing log lines that cannot be told apart.
     *
     * @param eventType topic the delivery came from, e.g. {@code order.created}
     * @param eventId   delivery id; null means the producer sent none and nothing can be matched
     */
    private boolean isRedelivery(String eventType, String eventId) {
        if (eventId == null || notificationRepository.findByEventId(eventId).isEmpty()) {
            return false;
        }
        log.info("Dropping redelivery of {} event {}", eventType, eventId);
        return true;
    }

    /** Avro strings arrive as {@link CharSequence}, not {@link String}. */
    private static String text(CharSequence value) {
        return value == null ? null : value.toString();
    }

    /** Avro decimal(19,2) arrives as unscaled big-endian bytes. */
    private static BigDecimal decimal(ByteBuffer value) {
        if (value == null) {
            return null;
        }
        byte[] bytes = new byte[value.remaining()];
        value.get(bytes);
        return new BigDecimal(new BigInteger(bytes), 2);
    }

    @Transactional
    public void markAsSent(String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        notification.setSent(true);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    public java.util.List<Notification> getNotificationsByUserId(String userId) {
        return notificationRepository.findByUserId(userId);
    }

    public java.util.List<Notification> getUnsentNotificationsByUserId(String userId) {
        return notificationRepository.findByUserIdAndSent(userId, false);
    }
}
