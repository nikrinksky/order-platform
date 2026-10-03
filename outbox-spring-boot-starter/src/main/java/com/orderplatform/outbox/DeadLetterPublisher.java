package com.orderplatform.outbox;

/**
 * Parks an event that ran out of its retry budget somewhere it can still be found.
 *
 * <p>Called by {@link OutboxRelay} only. Dropping such an event would be the worst possible
 * outcome of a broker incident: the business transaction committed, so the event is real, but
 * nobody would ever learn that it never arrived.
 */
public interface DeadLetterPublisher {

    /**
     * @param reason  why the record gave up, never parsed - it is for the person reading the topic
     * @throws RuntimeException when the dead letter destination itself is unreachable; the relay
     *                          then keeps the record instead of losing it
     */
    void send(OutboxMessage message, String reason);
}
