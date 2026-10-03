package com.orderplatform.outbox;

import com.orderplatform.events.EventHeaders;

/**
 * Headers the outbox adds to every broker record.
 *
 * <p>The names live in {@link EventHeaders} in the contracts module: consumers that do not use
 * this starter still need them, and a copy here would drift.
 */
public final class OutboxHeaders {

    /** @see EventHeaders#EVENT_ID */
    public static final String EVENT_ID = EventHeaders.EVENT_ID;

    /** @see EventHeaders#DEAD_LETTER_SOURCE_TOPIC */
    public static final String DEAD_LETTER_SOURCE_TOPIC = EventHeaders.DEAD_LETTER_SOURCE_TOPIC;

    private OutboxHeaders() {
    }
}
