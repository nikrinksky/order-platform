package com.orderplatform.outbox;

/**
 * Life cycle of an event stored in the transactional outbox.
 */
public enum OutboxRecordStatus {

    /** Written inside the business transaction, waiting to be handed to the broker. */
    NEW,

    /**
     * Claimed by one relay instance. Other instances skip it until the claim times out, which is
     * what keeps a multi replica service from publishing the same event twice at the same time.
     */
    IN_FLIGHT,

    /** Accepted by the broker; the record is kept for audit and de-duplication. */
    PUBLISHED,

    /** Retry budget exhausted; the record was parked on the dead-letter topic. */
    DEAD
}
