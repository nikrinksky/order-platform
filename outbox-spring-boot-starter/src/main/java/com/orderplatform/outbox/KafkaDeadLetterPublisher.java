package com.orderplatform.outbox;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Writes events that gave up on their retries to {@code <topic><suffix>}.
 *
 * <p>The original Avro record is re-sent rather than the stored JSON, so the dead letter topic
 * stays readable with the very same deserializer the healthy consumers use, and a replay is a
 * plain copy back to the source topic.
 *
 * <p>A dead letter topic that cannot be written to is reported, not swallowed: the relay keeps
 * the record in that case, because "we could not even park it" must stay visible.
 */
public class KafkaDeadLetterPublisher implements DeadLetterPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaDeadLetterPublisher.class);

    /** Why the record was parked, for whoever reads the topic. */
    public static final String REASON = "x-dlq-reason";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EventPayloadCodec codec;
    private final String deadLetterSuffix;
    private final long sendTimeoutMs;

    public KafkaDeadLetterPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                    EventPayloadCodec codec,
                                    String deadLetterSuffix,
                                    long sendTimeoutMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.codec = codec;
        this.deadLetterSuffix = deadLetterSuffix;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Override
    public void send(OutboxMessage message, String reason) {
        String destination = message.getTopic() + deadLetterSuffix;
        SpecificRecordBase payload = codec.fromJson(message.getEventType(), message.getPayload());

        ProducerRecord<String, Object> record = new ProducerRecord<>(destination, null,
                message.getEventKey(), payload);
        record.headers().add(new RecordHeader(OutboxHeaders.EVENT_ID,
                message.getEventId().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(OutboxHeaders.DEAD_LETTER_SOURCE_TOPIC,
                message.getTopic().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(REASON, truncate(reason).getBytes(StandardCharsets.UTF_8)));

        try {
            kafkaTemplate.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            log.warn("Parked outbox event {} from topic {} on {}",
                    message.getEventId(), message.getTopic(), destination);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while parking event " + message.getEventId(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot park event " + message.getEventId()
                    + " on " + destination, e);
        }
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return "";
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }
}
