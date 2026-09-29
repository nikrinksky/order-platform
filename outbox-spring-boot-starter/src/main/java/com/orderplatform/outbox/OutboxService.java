package com.orderplatform.outbox;

import org.apache.avro.specific.SpecificRecordBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends events to the outbox.
 *
 * <p>{@link Propagation#REQUIRED} is the whole point: when this is called from a business method
 * the insert joins that transaction, so the state change and its event become durable together.
 * Publishing happens later in {@link OutboxRelay}, which is why a broker outage can no longer
 * lose an event or block the API call.
 */
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository repository;
    private final EventPayloadCodec codec;

    public OutboxService(OutboxRepository repository, EventPayloadCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    /**
     * Stores an Avro event so it reaches {@code topic} under {@code aggregateId} at least once.
     *
     * <p>{@code eventId} is the idempotency key of the append and comes from the business
     * operation, normally {@code <aggregateId>:<WHAT_HAPPENED>}. It is deliberately not a hash of
     * the payload: two legitimate events can carry identical data, and a hash would quietly drop
     * the second one.
     *
     * @return the stored record, or the existing one when the same event was appended before
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public OutboxRecord append(String topic, String aggregateId, String eventId,
                               SpecificRecordBase event) {
        return save(topic, aggregateId, eventId, event);
    }

    /**
     * Appends an event that has no natural idempotency key.
     *
     * <p>Nothing recognises a repeated append here, so a caller that retried a failed business
     * transaction can store the same event twice. Prefer the four argument form whenever the
     * operation has a name worth reusing as a key.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public OutboxRecord append(String topic, String aggregateId, SpecificRecordBase event) {
        String eventId = aggregateId + ":" + codec.eventTypeOf(event) + ":" + System.nanoTime();
        log.debug("Appending to {} without an idempotency key, key generated as {}", topic, eventId);
        return save(topic, aggregateId, eventId, event);
    }

    private OutboxRecord save(String topic, String aggregateId, String eventId,
                              SpecificRecordBase event) {
        String eventType = codec.eventTypeOf(event);
        String payload = codec.toJson(event);

        return repository.findByEventId(eventId).orElseGet(() -> repository.save(
                OutboxRecord.from(OutboxAppendRequest.builder()
                        .eventId(eventId)
                        .topic(topic)
                        .aggregateId(aggregateId)
                        .eventType(eventType)
                        .payload(payload)
                        .build())));
    }
}
