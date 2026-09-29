package com.orderplatform.outbox;

import lombok.Builder;
import lombok.Getter;

/**
 * Everything the outbox needs to know about an event that is about to be appended.
 */
@Getter
@Builder
public class OutboxAppendRequest {

    /** De-duplication identifier, derived from the payload by {@link OutboxService}. */
    private final String eventId;

    /** Destination topic. */
    private final String topic;

    /** Partition key, e.g. the order id. */
    private final String aggregateId;

    /** Event schema name, e.g. {@code OrderCreatedEvent}. */
    private final String eventType;

    /** Event body as JSON. */
    private final String payload;
}
