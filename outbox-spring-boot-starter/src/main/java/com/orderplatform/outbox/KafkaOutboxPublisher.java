package com.orderplatform.outbox;

import org.apache.avro.specific.SpecificRecordBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends one outbox event to the broker and waits until the brokers acknowledged it.
 *
 * <p>Waiting is the point. A fire-and-forget send returns before the record is durable, so the
 * caller cannot tell success from failure and the outbox row would be closed for an event that
 * never arrived. With {@code acks=all} plus a wait, the row is only marked published once the
 * event is really on the topic.
 *
 * <p>The KafkaTemplate is handed an Avro {@link SpecificRecordBase}: its configured value
 * serializer is {@code KafkaAvroSerializer}, which looks the schema up in the registry and
 * registers it when auto-registration is on.
 */
public class KafkaOutboxPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EventPayloadCodec codec;
    private final long sendTimeoutMs;

    public KafkaOutboxPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                               EventPayloadCodec codec,
                               long sendTimeoutMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.codec = codec;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    /**
     * Bounded in-process retry for a broker that is briefly unreachable. The budget is small on
     * purpose: the outbox already reschedules the record in the database, and sleeps here would
     * stack on top of the producer's own retries and stall the whole relay batch.
     */
    @Override
    @Retryable(retryFor = Exception.class, noRetryFor = IllegalArgumentException.class,
            maxAttemptsExpression = "${outbox.retry.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${outbox.retry.initial-backoff-ms:200}",
                    maxDelayExpression = "${outbox.retry.max-backoff-ms:2000}",
                    multiplierExpression = "${outbox.retry.multiplier:2.0}"))
    public void publish(OutboxMessage message) {
        SpecificRecordBase payload = codec.fromJson(message.getEventType(), message.getPayload());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                MessageBuilder.withPayload(payload)
                        .setHeader(org.springframework.kafka.support.KafkaHeaders.TOPIC, message.getTopic())
                        .setHeader(org.springframework.kafka.support.KafkaHeaders.KEY, message.getEventKey())
                        .setHeader(OutboxHeaders.EVENT_ID, message.getEventId())
                        .build());
        try {
            future.get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            log.debug("Published outbox event {} to topic {}", message.getEventId(), message.getTopic());
        } catch (TimeoutException e) {
            // The broker may still accept the record after the deadline, so retrying a timed out
            // event can duplicate it. Consumers de-duplicate on the event id header.
            throw new IllegalStateException(
                    "Broker did not acknowledge event " + message.getEventId() + " within " + sendTimeoutMs + " ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing event " + message.getEventId(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot publish event " + message.getEventId(), e);
        }
    }
}
