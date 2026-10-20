package com.orderplatform.outbox;

import com.orderplatform.events.EventHeaders;
import com.orderplatform.events.order.OrderCreatedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The record parked on the dead-letter topic must stay traceable back to the business event it
 * came from, otherwise a replay after an incident cannot be matched to anything.
 */
@ExtendWith(MockitoExtension.class)
class KafkaDeadLetterPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final EventPayloadCodec codec = new EventPayloadCodec(
            Map.of("OrderCreatedEvent", OrderCreatedEvent.class));

    @Test
    void send_shouldParkOnTheDeadLetterTopicWithTheBusinessEventId() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mockSendResult()));

        new KafkaDeadLetterPublisher(kafkaTemplate, codec, ".DLT", 1000)
                .send(message("order-1:CREATED"), "IllegalStateException: broker down");

        ProducerRecord<String, Object> parked = capturedRecord();
        assertThat(parked.topic()).isEqualTo("order.created.DLT");
        assertThat(header(parked, EventHeaders.EVENT_ID)).isEqualTo("order-1:CREATED");
        assertThat(header(parked, EventHeaders.DEAD_LETTER_SOURCE_TOPIC)).isEqualTo("order.created");
    }

    private static String header(ProducerRecord<String, Object> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, Object> capturedRecord() {
        ArgumentCaptor<ProducerRecord<String, Object>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }

    private static OutboxMessage message(String eventId) {
        return new OutboxMessage(eventId, "order.created", "order-1", "OrderCreatedEvent",
                event().toString(), 10);
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
