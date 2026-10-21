package com.orderplatform.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A record handed to an {@link OutboxPublisher}.
 *
 * <p>Snapshot rather than managed entity on purpose: the relay publishes outside the transaction
 * that claimed the row, so the publisher must not be able to touch that session.
 */
@Getter
@AllArgsConstructor
public class OutboxMessage {

    /**
     * Identifier a consumer de-duplicates on, taken from the outbox {@code event_id} column.
     *
     * <p>It is the <em>business</em> key the producer chose - {@code order-1:CREATED}, for
     * instance - and not the surrogate row id. Consumers correlate an event with the operation that
     * produced it from this value alone, and a row id would tell them nothing: it is assigned by
     * this service's own sequence and changes on every append.
     *
     * <p>Stability across redeliveries is what makes it usable as a de-duplication key: the relay
     * republishes the same row, so a redelivery always carries the same value, and the unique
     * {@code event_id} column keeps the same business event from being appended twice.
     */
    private final String eventId;

    private final String topic;

    /** Partition key, stored as {@code aggregate_id}. */
    private final String eventKey;

    private final String eventType;

    private final String payload;

    private final int attempts;

    @Override
    public String toString() {
        return "OutboxMessage[eventId=" + eventId + ", topic=" + topic + ", eventKey=" + eventKey +
                ", eventType=" + eventType + ", attempts=" + attempts + "]";
    }
}
