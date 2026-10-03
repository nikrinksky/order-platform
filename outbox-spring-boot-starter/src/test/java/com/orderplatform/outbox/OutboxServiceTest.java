package com.orderplatform.outbox;

import com.orderplatform.events.order.OrderCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository repository;

    @Mock
    private EventPayloadCodec codec;

    @InjectMocks
    private OutboxService service;

    @Test
    void append_shouldStoreNewEventAsNew() {
        when(codec.eventTypeOf(any())).thenReturn("OrderCreatedEvent");
        when(codec.toJson(any())).thenReturn("{\"orderId\":\"order-1\"}");
        when(repository.findByEventId("order-1:CREATED")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OutboxRecord stored = service.append("order.created", "order-1", "order-1:CREATED",
                sampleEvent());

        assertThat(stored.getEventId()).isEqualTo("order-1:CREATED");
        assertThat(stored.getTopic()).isEqualTo("order.created");
        assertThat(stored.getAggregateId()).isEqualTo("order-1");
        assertThat(stored.getStatus()).isEqualTo(OutboxRecordStatus.NEW);
        assertThat(stored.getAttempts()).isZero();
        assertThat(stored.getNextAttemptAt()).isNotNull();
    }

    @Test
    void append_shouldReturnExistingRecordForSameEventId() {
        when(codec.eventTypeOf(any())).thenReturn("OrderCreatedEvent");
        when(codec.toJson(any())).thenReturn("{}");
        OutboxRecord existing = OutboxRecord.builder()
                .eventId("order-1:CREATED")
                .status(OutboxRecordStatus.PUBLISHED)
                .build();
        when(repository.findByEventId("order-1:CREATED")).thenReturn(Optional.of(existing));

        OutboxRecord stored = service.append("order.created", "order-1", "order-1:CREATED",
                sampleEvent());

        // A retried business operation must not produce a second event
        assertThat(stored).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    /** Every non-null field of the contract has to be set: Avro builders enforce it. */
    private static OrderCreatedEvent sampleEvent() {
        return OrderCreatedEvent.newBuilder()
                .setOrderId("order-1")
                .setUserId("user-1")
                .setOrderNumber("ORD-ABC12345")
                .setTotalAmount(ByteBuffer.wrap(
                        new BigDecimal("149.90").setScale(2).unscaledValue().toByteArray()))
                .build();
    }
}
