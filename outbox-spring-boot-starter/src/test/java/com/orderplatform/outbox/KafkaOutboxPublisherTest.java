package com.orderplatform.outbox;

import com.orderplatform.events.EventHeaders;
import com.orderplatform.events.order.OrderCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins down what a consumer actually sees in the {@code x-event-id} header.
 *
 * <p>The value has to be the business key the producer chose, not the surrogate row id of the
 * outbox table: consumers correlate an event with the operation that produced it, and a row id
 * says nothing about which order or user the record belongs to.
 */
@ExtendWith(MockitoExtension.class)
class KafkaOutboxPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final EventPayloadCodec codec = new EventPayloadCodec(
            Map.of("OrderCreatedEvent", OrderCreatedEvent.class));

    @Test
    void publish_shouldCarryTheBusinessEventIdInTheHeader() {
        when(kafkaTemplate.send(any(Message.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        new KafkaOutboxPublisher(kafkaTemplate, codec, 1000)
                .publish(message("order-1:CREATED"));

        Message<?> sent = capturedMessage();
        assertThat(sent.getHeaders().get(EventHeaders.EVENT_ID))
                .isEqualTo("order-1:CREATED");
    }

    @Test
    void publish_shouldKeepTheBusinessKeyDistinctFromTheRowId() {
        when(kafkaTemplate.send(any(Message.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        // A row id of 42 and a business key that mentions neither the topic nor the sequence
        new KafkaOutboxPublisher(kafkaTemplate, codec, 1000)
                .publish(message("order-1:STATUS:RESERVED"));

        assertThat(capturedMessage().getHeaders().get(EventHeaders.EVENT_ID))
                .isEqualTo("order-1:STATUS:RESERVED")
                .isNotEqualTo("42");
    }

    private Message<?> capturedMessage() {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }

    private static OutboxMessage message(String eventId) {
        return new OutboxMessage(eventId, "order.created", "order-1", "OrderCreatedEvent",
                event().toString(), 1);
    }

    private static SendResult<String, Object> mockSendResult() {
        return org.mockito.Mockito.mock(SendResult.class);
    }

    /** Every non-null field of the contract has to be set: Avro builders enforce it. */
    private static OrderCreatedEvent event() {
        return OrderCreatedEvent.newBuilder()
                .setOrderId("order-1")
                .setUserId("user-1")
                .setOrderNumber("ORD-ABC12345")
                .setTotalAmount(ByteBuffer.wrap(
                        new BigDecimal("149.90").setScale(2).unscaledValue().toByteArray()))
                .build();
    }
}
