package com.orderplatform.events;

/**
 * Headers every event on the bus carries. Producers set them, consumers rely on them, so the
 * names live with the event contracts rather than in any single service.
 */
public final class EventHeaders {

    /**
     * Delivery id of the event. Consumers use it to recognise a redelivery, which is the
     * consumer-side half of "at-least-once delivery, effectively once processing".
     */
    public static final String EVENT_ID = "x-event-id";

    /** Original topic of a record that was parked on a dead-letter topic. */
    public static final String DEAD_LETTER_SOURCE_TOPIC = "x-dlq-source-topic";

    /** Why a record was parked on a dead-letter topic; for the person reading it, never parsed. */
    public static final String DEAD_LETTER_REASON = "x-dlq-reason";

    private EventHeaders() {
    }
}
