package com.orderplatform.notification.service;

import com.orderplatform.events.order.OrderCreatedEvent;
import com.orderplatform.events.order.OrderStatusChangedEvent;
import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
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
        OrderCreatedEvent event = orderCreated();

        Notification savedNotification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .type("ORDER_CREATED")
                .subject("Order Created: ORD-ABC12345")
                .sent(false)
                .build();

        when(notificationRepository.findByEventId("event-1")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

        assertDoesNotThrow(() -> notificationService.onOrderCreated(event, "event-1"));
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void onOrderCreated_shouldDropRedeliveryOfKnownEvent() {
        when(notificationRepository.findByEventId("event-1"))
                .thenReturn(Optional.of(Notification.builder().id("notif-1").build()));

        notificationService.onOrderCreated(orderCreated(), "event-1");

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void onOrderStatusChanged_shouldCreateNotification() {
        OrderStatusChangedEvent event = OrderStatusChangedEvent.newBuilder()
                .setOrderId("order-1")
                .setUserId("user-1")
                .setOrderNumber("ORD-ABC12345")
                .setStatus("PAID")
                .build();

        Notification savedNotification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .type("ORDER_STATUS_CHANGED")
                .subject("Order Status: PAID")
                .sent(false)
                .build();

        when(notificationRepository.findByEventId("event-2")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

        assertDoesNotThrow(() -> notificationService.onOrderStatusChanged(event, "event-2"));
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void onOrderStatusChanged_shouldStoreEvenWithoutEventId() {
        // A producer that sent no delivery id leaves nothing to match a redelivery on,
        // so the notification is stored rather than silently dropped
        OrderStatusChangedEvent event = OrderStatusChangedEvent.newBuilder()
                .setOrderId("order-1")
                .setUserId("user-1")
                .setOrderNumber("ORD-ABC12345")
                .setStatus("PAID")
                .build();

        when(notificationRepository.save(any(Notification.class)))
                .thenReturn(Notification.builder().id("notif-1").build());

        notificationService.onOrderStatusChanged(event, null);

        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    private static OrderCreatedEvent orderCreated() {
        return OrderCreatedEvent.newBuilder()
                .setOrderId("order-1")
                .setUserId("user-1")
                .setOrderNumber("ORD-ABC12345")
                .setTotalAmount(decimal("99.99"))
                .build();
    }

    /** Same encoding the producer uses for the Avro decimal(19,2) contract. */
    private static ByteBuffer decimal(String value) {
        return ByteBuffer.wrap(new BigDecimal(value).setScale(2).unscaledValue().toByteArray());
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
