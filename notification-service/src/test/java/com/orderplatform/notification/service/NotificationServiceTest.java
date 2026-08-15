package com.orderplatform.notification.service;

import com.orderplatform.notification.dto.OrderCreatedEvent;
import com.orderplatform.notification.dto.OrderStatusChangedEvent;
import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
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
    void onOrderStatusChanged_shouldCreateNotification() {
        OrderStatusChangedEvent event = OrderStatusChangedEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .orderNumber("ORD-ABC12345")
                .status("PAID")
                .build();

        Notification savedNotification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .type("ORDER_STATUS_CHANGED")
                .subject("Order Status: PAID")
                .sent(false)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

        assertDoesNotThrow(() -> notificationService.onOrderStatusChanged(event));
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

    @Test
    void markAsSent_shouldThrowWhenNotFound() {
        when(notificationRepository.findById("999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> notificationService.markAsSent("999"));
    }

    @Test
    void getNotificationsByUserId_shouldReturnList() {
        Notification notification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .build();
        when(notificationRepository.findByUserId("user-1")).thenReturn(List.of(notification));

        List<Notification> result = notificationService.getNotificationsByUserId("user-1");

        assertEquals(1, result.size());
        assertEquals("user-1", result.get(0).getUserId());
    }

    @Test
    void getUnsentNotificationsByUserId_shouldReturnUnsentOnly() {
        Notification unsent = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .sent(false)
                .build();
        when(notificationRepository.findByUserIdAndSent("user-1", false)).thenReturn(List.of(unsent));

        List<Notification> result = notificationService.getUnsentNotificationsByUserId("user-1");

        assertEquals(1, result.size());
        assertFalse(result.get(0).isSent());
    }
}
