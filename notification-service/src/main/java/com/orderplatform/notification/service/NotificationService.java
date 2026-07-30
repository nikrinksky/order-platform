package com.orderplatform.notification.service;

import com.orderplatform.notification.dto.OrderCreatedEvent;
import com.orderplatform.notification.dto.OrderStatusChangedEvent;
import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Processing order created event for order: {}", event.getOrderId());

        Notification notification = Notification.builder()
                .userId(event.getUserId())
                .type("ORDER_CREATED")
                .subject("Order Created: " + event.getOrderNumber())
                .body("Your order " + event.getOrderNumber() + " with total amount $" + event.getTotalAmount() + " has been created.")
                .channel("EMAIL")
                .sent(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
        log.info("Notification created for order: {}", event.getOrderId());
    }

    @Transactional
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Processing order status changed event for order: {}", event.getOrderId());

        Notification notification = Notification.builder()
                .userId(event.getUserId())
                .type("ORDER_STATUS_CHANGED")
                .subject("Order Status: " + event.getStatus())
                .body("Your order " + event.getOrderNumber() + " status has been updated to " + event.getStatus() + ".")
                .channel("EMAIL")
                .sent(false)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
        log.info("Notification created for status change: {}", event.getOrderId());
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
