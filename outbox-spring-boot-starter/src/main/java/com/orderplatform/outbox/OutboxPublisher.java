package com.orderplatform.outbox;

/**
 * Hands one outbox record to the messaging infrastructure.
 *
 * <p>Implemented per transport (Kafka today) and called only by {@link OutboxRelay}, so business
 * code never talks to a broker directly.
 */
@FunctionalInterface
public interface OutboxPublisher {

    /**
     * @throws RuntimeException when the broker did not accept the record; the relay then reschedules it
     */
    void publish(OutboxMessage message);
}
