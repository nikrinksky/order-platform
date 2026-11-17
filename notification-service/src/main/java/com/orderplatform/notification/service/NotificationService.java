package com.orderplatform.notification.service;

import com.orderplatform.events.order.OrderCreatedEvent;
import com.orderplatform.events.order.OrderStatusChangedEvent;
import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.model.UserContact;
import com.orderplatform.notification.repository.NotificationRepository;
import com.orderplatform.notification.repository.UserContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserContactRepository userContactRepository;
    private final EmailSender emailSender;
    private final NotificationRateLimiter rateLimiter;

    /** Delivery attempts before a notification is left unsent for inspection. */
    @Value("${notification.retry.max-attempts:5}")
    private int maxAttempts;

    /** Base delay between attempts; doubles after each failure. */
    @Value("${notification.retry.backoff-ms:60000}")
    private long backoffMs;

    /**
     * Stores the notification for a created order and tries to deliver it.
     *
     * <p>Not transactional on purpose: the record is written first and committed on its own, so a
     * mail server failure cannot roll it back. It used to be one transaction, which meant a
     * transient SMTP outage erased the notification entirely - the event was then retried by
     * Kafka a few times and parked on the DLT, and nothing was left to retry or inspect.
     *
     * @param eventId delivery id of the Kafka record; null when the producer sent no id, in
     *                which case a redelivery would be stored again - there is nothing to match on
     */
    public void onOrderCreated(OrderCreatedEvent event, String eventId) {
        if (handledAsRedelivery("order.created", eventId)) {
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
                // Due right away: a crash between the write and the send is picked up by the retry
                .nextAttemptAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
        log.info("Notification created for order: {}", event.getOrderId());
        deliver(notification);
    }

    public void onOrderStatusChanged(OrderStatusChangedEvent event, String eventId) {
        if (handledAsRedelivery("order.status-changed", eventId)) {
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
                .nextAttemptAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
        log.info("Notification created for status change: {}", event.getOrderId());
        deliver(notification);
    }

    /**
     * Attempts one delivery and records the outcome.
     *
     * <p>A failure is not thrown: the notification is already stored, so the Kafka offset may be
     * acknowledged and the retry job owns the next attempt. Throwing would only make the listener
     * replay the event, which spends one of its bounded retries on an attempt the job would make
     * anyway - and the redelivery is used for that instead, see
     * {@link #handledAsRedelivery(String, String)}.
     *
     * <p>When no address is known the attempt is recorded as failed too, so the notification is
     * picked up again once the contact projection catches up instead of staying unsent forever.
     *
     * <p>A user over their rate limit gets no attempt at all: the record is only rescheduled, see
     * {@link #defer(Notification)}.
     *
     * @param notification the stored notification to deliver
     */
    public void deliver(Notification notification) {
        String email = addressOf(notification.getUserId());
        if (email == null) {
            recordFailure(notification, "no email known for user " + notification.getUserId());
            return;
        }
        if (!rateLimiter.tryAcquire(notification.getUserId())) {
            defer(notification);
            return;
        }

        try {
            emailSender.send(email, notification.getSubject(), notification.getBody());
        } catch (RuntimeException e) {
            recordFailure(notification, e.getMessage());
            return;
        }

        notification.setSent(true);
        notification.setSentAt(LocalDateTime.now());
        notification.setAttempts(notification.getAttempts() + 1);
        notification.setLastError(null);
        notification.setNextAttemptAt(null);
        notificationRepository.save(notification);
        log.info("Notification {} sent to {} after {} attempt(s)",
                notification.getId(), email, notification.getAttempts());
    }

    /**
     * Keeps the record, notes why the attempt failed and schedules the next one.
     *
     * <p>The schedule is dropped once the attempts run out: the notification stays {@code sent=false}
     * with the last error attached, which is visible in the notification history, instead of being
     * retried forever against a mail server that will not take it.
     */
    private void recordFailure(Notification notification, String error) {
        int attempts = notification.getAttempts() + 1;
        notification.setAttempts(attempts);
        notification.setLastError(error);

        if (attempts >= maxAttempts) {
            notification.setNextAttemptAt(null);
            notificationRepository.save(notification);
            log.error("Notification {} could not be delivered in {} attempts, giving up: {}",
                    notification.getId(), attempts, error);
            return;
        }

        // Exponential backoff: 1x, 2x, 4x ... of the base delay, so a mail server that is down
        // for a while is not hammered every minute.
        long delay = backoffMs * (1L << Math.min(attempts - 1, 5));
        notification.setNextAttemptAt(LocalDateTime.now().plusNanos(delay * 1_000_000));
        notificationRepository.save(notification);
        log.warn("Notification {} delivery failed (attempt {}/{}), retrying in {} ms: {}",
                notification.getId(), attempts, maxAttempts, delay, error);
    }

    /**
     * Holds a notification back until the user's bucket has a token again.
     *
     * <p>A rate limit is not a delivery failure: the attempt counter and the last error are left
     * alone, so a burst of events neither burns the bounded attempts nor shows up in the history
     * as a failed delivery. The retry job picks the record up once the delay has passed, which is
     * why nothing is lost - the message is only moved in time.
     */
    private void defer(Notification notification) {
        long delay = rateLimiter.retryDelayMs();
        notification.setNextAttemptAt(LocalDateTime.now().plusNanos(delay * 1_000_000));
        notificationRepository.save(notification);
        log.info("Notification {} for user {} is held back by the rate limit, next attempt in {} ms",
                notification.getId(), notification.getUserId(), delay);
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
     * Recognises a redelivery and, when the earlier attempt never went out, makes another one.
     *
     * <p>Delivery is at-least-once, so the same event can arrive twice. A stored record used to be
     * dropped on sight, which is right only for a delivered one: for an undelivered notification
     * the offset was acknowledged, the event was gone, and the record waited for the retry job.
     * A redelivery is a free attempt, so it is used while the notification is still unsent and
     * attempts are left.
     *
     * @param eventType topic the delivery came from, e.g. {@code order.created}
     * @param eventId   delivery id; null means the producer sent none and nothing can be matched
     * @return true when the event needs no further processing
     */
    private boolean handledAsRedelivery(String eventType, String eventId) {
        if (eventId == null) {
            return false;
        }
        final Optional<Notification> existing = notificationRepository.findByEventId(eventId);
        if (existing.isEmpty()) {
            return false;
        }
        final Notification notification = existing.get();
        if (notification.isSent()) {
            log.info("Dropping redelivery of {} event {}: notification {} was already delivered",
                    eventType, eventId, notification.getId());
            return true;
        }
        if (notification.getAttempts() >= maxAttempts) {
            log.warn("Dropping redelivery of {} event {}: notification {} used all {} attempts",
                    eventType, eventId, notification.getId(), maxAttempts);
            return true;
        }
        log.info("Redelivery of {} event {} retries the undelivered notification {}",
                eventType, eventId, notification.getId());
        deliver(notification);
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
