package com.orderplatform.notification.config;

import com.orderplatform.events.EventHeaders;
import com.orderplatform.events.order.OrderCreatedEvent;
import com.orderplatform.events.order.OrderStatusChangedEvent;
import com.orderplatform.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes the order events and stores one notification per event.
 *
 * <p>The offset is acknowledged only after the notification is stored: with manual ack a crash
 * between receive and store replays the event instead of losing it. A replay is then dropped by
 * the event id check in the service, which is what makes the pair "at-least-once delivery,
 * effectively once processing".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = "order.created", groupId = "notification-service-group")
    public void onOrderCreated(OrderCreatedEvent event,
                               @Header(name = EventHeaders.EVENT_ID, required = false) String eventId,
                               Acknowledgment acknowledgment) {
        log.info("Received order.created event: {}", event.getOrderId());
        notificationService.onOrderCreated(event, eventId);
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = "order.status-changed", groupId = "notification-service-group")
    public void onOrderStatusChanged(OrderStatusChangedEvent event,
                                     @Header(name = EventHeaders.EVENT_ID, required = false) String eventId,
                                     Acknowledgment acknowledgment) {
        log.info("Received order.status-changed event: {}", event.getOrderId());
        notificationService.onOrderStatusChanged(event, eventId);
        acknowledgment.acknowledge();
    }
}
