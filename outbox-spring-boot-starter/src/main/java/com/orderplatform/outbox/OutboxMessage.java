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

    /** Outbox row id; doubles as the delivery id, see {@link #getEventId()}. */
    private final Long id;

    private final String topic;

    /** Partition key, stored as {@code aggregate_id}. */
    private final String eventKey;

    private final String eventType;

    private final String payload;

    private final int attempts;

    /**
     * Identifier a consumer de-duplicates on.
     *
     * <p>It is the outbox row id rather than a hash of the payload: the relay republishes the same
     * row, so a redelivery always carries the same value, while two different events never share
     * one. The unique {@code event_id} column covers the other half - it keeps the same business
     * event from being appended twice.
     */
    public String getEventId() {
        return id == null ? null : Long.toString(id);
    }

    @Override
    public String toString() {
        return "OutboxMessage[id=" + id + ", topic=" + topic + ", eventKey=" + eventKey
                + ", eventType=" + eventType + ", attempts=" + attempts + "]";
    }
}
