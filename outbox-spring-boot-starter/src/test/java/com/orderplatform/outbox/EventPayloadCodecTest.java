package com.orderplatform.outbox;

import com.orderplatform.events.order.OrderCreatedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventPayloadCodecTest {

    private final EventPayloadCodec codec = new EventPayloadCodec(Map.of(
            "OrderCreatedEvent", OrderCreatedEvent.class));

    @Test
    void shouldRoundTripAnEventThroughJson() {
        OrderCreatedEvent event = OrderCreatedEvent.newBuilder()
                .setOrderId("order-1")
                .setUserId("user-1")
                .setOrderNumber("ORD-ABC12345")
                .setTotalAmount(decimal("149.90"))
                .build();

        String json = codec.toJson(event);
        OrderCreatedEvent copy = (OrderCreatedEvent) codec.fromJson("OrderCreatedEvent", json);

        // Avro rebuilds CharSequence fields as Utf8, so compare through toString()
        assertThat(copy.getOrderId().toString()).isEqualTo("order-1");
        assertThat(copy.getUserId().toString()).isEqualTo("user-1");
        assertThat(copy.getOrderNumber().toString()).isEqualTo("ORD-ABC12345");
        // The decimal must survive the round trip byte for byte
        assertThat(decimalToString(copy.getTotalAmount())).isEqualTo("149.90");
    }

    @Test
    void shouldRejectUnmappedEventType() {
        assertThatThrownBy(() -> codec.fromJson("UnknownEvent", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UnknownEvent");
    }

    @Test
    void shouldFailFastOnClassWithoutNoArgConstructor() {
        assertThatThrownBy(() -> new EventPayloadCodec(Map.of("Broken", BrokenEvent.class)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** A SpecificRecordBase without a public no-arg constructor cannot be rebuilt on publish. */
    public static class BrokenEvent extends OrderCreatedEvent {
        public BrokenEvent(String arg) {
            super();
        }
    }

    private static ByteBuffer decimal(String value) {
        return ByteBuffer.wrap(new BigDecimal(value).setScale(2).unscaledValue().toByteArray());
    }

    private static String decimalToString(ByteBuffer value) {
        byte[] bytes = new byte[value.remaining()];
        value.get(bytes);
        return new BigDecimal(new java.math.BigInteger(bytes), 2).toPlainString();
    }
}
