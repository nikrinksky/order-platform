package com.orderplatform.notification.service;

import com.orderplatform.events.order.OrderCreatedEvent;
import com.orderplatform.events.order.OrderStatusChangedEvent;
import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.repository.NotificationRepository;
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

    /**
     * Stores the notification for a created order.
     *
     * @param eventId delivery id of the Kafka record; null when the producer sent no id, in
     *                which case a redelivery would be stored again - there is nothing to match on
     */
    @Transactional
    public void onOrderCreated(OrderCreatedEvent event, String eventId) {
        if (eventId != null && notificationRepository.findByEventId(eventId).isPresent()) {
            log.info("Notification for event {} already stored, dropping the redelivery", eventId);
            return;
        }
        log.info("Processing order created event for order: {}", event.getOrderId());

        Notification notification = Notification.builder()
                .userId(text(event.getUserId()))
                .eventId(eventId)
                .type("ORDER_CREATED")
                .subject("Order Created: " + text(event.getOrderNumber()))
                .body("Your order " + text(event.getOrderNumber()) + " with total amount $"
                        + decimal(event.getTotalAmount()) + " has been created.")
                .channel("EMAIL")
                .sent(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
        log.info("Notification created for order: {}", event.getOrderId());
    }

    @Transactional
    public void onOrderStatusChanged(OrderStatusChangedEvent event, String eventId) {
        if (eventId != null && notificationRepository.findByEventId(eventId).isPresent()) {
            log.info("Notification for event {} already stored, dropping the redelivery", eventId);
            return;
        }
        log.info("Processing order status changed event for order: {}", event.getOrderId());

        Notification notification = Notification.builder()
                .userId(text(event.getUserId()))
                .eventId(eventId)
                .type("ORDER_STATUS_CHANGED")
                .subject("Order Status: " + text(event.getStatus()))
                .body("Your order " + text(event.getOrderNumber()) + " status has been updated to "
                        + text(event.getStatus()) + ".")
                .channel("EMAIL")
                .sent(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
        log.info("Notification created for status change: {}", event.getOrderId());
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
