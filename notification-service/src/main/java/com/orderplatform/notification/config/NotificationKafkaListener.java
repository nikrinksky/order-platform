package com.orderplatform.notification.config;

import com.orderplatform.notification.dto.OrderCreatedEvent;
import com.orderplatform.notification.dto.OrderStatusChangedEvent;
import com.orderplatform.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaListener {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "order.created",
            groupId = "notification-service-group",
            properties = {"spring.json.value.default.type=com.orderplatform.notification.dto.OrderCreatedEvent"}
    )
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received order.created event: {}", event.getOrderId());
        notificationService.onOrderCreated(event);
    }

    @KafkaListener(
            topics = "order.status-changed",
            groupId = "notification-service-group",
            properties = {"spring.json.value.default.type=com.orderplatform.notification.dto.OrderStatusChangedEvent"}
    )
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Received order.status-changed event: {}", event.getOrderId());
        notificationService.onOrderStatusChanged(event);
    }
}
