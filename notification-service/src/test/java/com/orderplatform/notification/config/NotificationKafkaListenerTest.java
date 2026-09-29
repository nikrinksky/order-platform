package com.orderplatform.notification.config;

import com.orderplatform.events.order.OrderCreatedEvent;
import com.orderplatform.events.order.OrderStatusChangedEvent;
import com.orderplatform.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private NotificationKafkaListener listener;

    @Test
    void onOrderCreated_shouldDelegateAndAck() {
        OrderCreatedEvent event = orderCreated();

        listener.onOrderCreated(event, "event-1", acknowledgment);

        verify(notificationService, times(1)).onOrderCreated(event, "event-1");
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void onOrderStatusChanged_shouldDelegateAndAck() {
        OrderStatusChangedEvent event = OrderStatusChangedEvent.newBuilder()
                .setOrderId("order-1")
                .setUserId("user-1")
                .setOrderNumber("ORD-ABC12345")
                .setStatus("PAID")
                .build();

        listener.onOrderStatusChanged(event, "event-2", acknowledgment);

        verify(notificationService, times(1)).onOrderStatusChanged(event, "event-2");
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void onOrderCreated_shouldNotAckWhenHandlerFails() {
        OrderCreatedEvent event = orderCreated();
        org.mockito.Mockito.doThrow(new RuntimeException("handler failed"))
                .when(notificationService).onOrderCreated(event, "event-1");

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> listener.onOrderCreated(event, "event-1", acknowledgment))
                .isInstanceOf(RuntimeException.class);

        // No ack on failure: the record is redelivered, which is the point of manual ack
        verifyNoInteractions(acknowledgment);
    }

    /** Every non-null field of the contract has to be set: Avro builders enforce it. */
    private static OrderCreatedEvent orderCreated() {
        return OrderCreatedEvent.newBuilder()
                .setOrderId("order-1")
                .setUserId("user-1")
                .setOrderNumber("ORD-ABC12345")
                .setTotalAmount(java.nio.ByteBuffer.wrap(
                        new java.math.BigDecimal("99.99").setScale(2).unscaledValue().toByteArray()))
                .build();
    }
}