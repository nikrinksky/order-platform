package com.orderplatform.notification.service;

import com.orderplatform.notification.dto.OrderCreatedEvent;
import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void onOrderCreated_shouldCreateNotification() {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .orderNumber("ORD-ABC12345")
                .totalAmount(new BigDecimal("99.99"))
                .build();

        Notification savedNotification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .type("ORDER_CREATED")
                .subject("Order Created: ORD-ABC12345")
                .sent(false)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

        assertDoesNotThrow(() -> notificationService.onOrderCreated(event));
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void markAsSent_shouldMarkNotificationAsSent() {
        Notification notification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .type("ORDER_CREATED")
                .sent(false)
                .build();

        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(notification));

        assertDoesNotThrow(() -> notificationService.markAsSent("notif-1"));
        assertTrue(notification.isSent());
        assertNotNull(notification.getSentAt());
        verify(notificationRepository, times(1)).save(notification);
    }
}
