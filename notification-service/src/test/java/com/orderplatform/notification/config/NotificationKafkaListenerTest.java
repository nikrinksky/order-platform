package com.orderplatform.notification.config;

import com.orderplatform.notification.dto.OrderCreatedEvent;
import com.orderplatform.notification.dto.OrderStatusChangedEvent;
import com.orderplatform.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationKafkaListener listener;

    @Test
    void onOrderCreated_shouldDelegateToService() {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .orderNumber("ORD-ABC12345")
                .totalAmount(new BigDecimal("99.99"))
                .build();

        listener.onOrderCreated(event);

        verify(notificationService, times(1)).onOrderCreated(event);
    }

    @Test
    void onOrderStatusChanged_shouldDelegateToService() {
        OrderStatusChangedEvent event = OrderStatusChangedEvent.builder()
                .orderId("order-1")
                .userId("user-1")
                .orderNumber("ORD-ABC12345")
                .status("PAID")
                .build();

        listener.onOrderStatusChanged(event);

        verify(notificationService, times(1)).onOrderStatusChanged(event);
    }
}