package com.orderplatform.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A domain event that has to reach the broker.
 *
 * <p>The row is inserted with {@code REQUIRED} propagation, i.e. inside the transaction of the
 * business operation that produced it, so the order state change and its event either both
 * exist or both do not. A separate relay process reads the table and publishes the rows, which
 * is what makes the delivery at-least-once rather than "best effort".
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "outbox_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_outbox_event_id", columnNames = "event_id"),
        indexes = @Index(name = "idx_outbox_status_next_attempt", columnList = "status,next_attempt_at")
)
public class OutboxRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stable identifier of the event, carried to consumers so they can drop duplicates.
     * Required: at-least-once delivery without a de-duplication key only moves the problem
     * to the consumer.
     */
    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    /** Kafka topic the record is published to. */
    @Column(nullable = false, length = 128)
    private String topic;

    /**
     * Partition key: the id of the aggregate the event belongs to. Every event of one aggregate
     * must keep the same key, otherwise Kafka can no longer preserve their order.
     */
    @Column(name = "aggregate_id", nullable = false, length = 128)
    private String aggregateId;

    /** Schema name of the event, used to rebuild the concrete event type on publish. */
    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    /** Event body as JSON. Kept readable on purpose: it is the forensic record of what happened. */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxRecordStatus status;

    @Column(nullable = false)
    private int attempts;

    /**
     * Earliest instant the record may be handed to the broker again. While a record is claimed
     * this doubles as the claim expiry, which is what releases the rows of a relay that died.
     */
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    public static OutboxRecord from(OutboxAppendRequest request) {
        Instant now = Instant.now();
        return OutboxRecord.builder()
                .eventId(request.getEventId())
                .topic(request.getTopic())
                .aggregateId(request.getAggregateId())
                .eventType(request.getEventType())
                .payload(request.getPayload())
                .status(OutboxRecordStatus.NEW)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .build();
    }

    /**
     * Claims the record for one publish attempt and hides it from other relays until the claim
     * expires. The attempt counter is increased here rather than on failure, so a relay that dies
     * mid publish still burns budget and cannot loop on the same record forever.
     */
    public void claim(long claimTimeoutMs) {
        this.attempts++;
        this.status = OutboxRecordStatus.IN_FLIGHT;
        this.nextAttemptAt = Instant.now().plusMillis(claimTimeoutMs);
    }

    /**
     * Records a failed delivery and schedules the next attempt.
     *
     * @param backoffMillis delay before the next attempt, already resolved by the caller
     */
    public void scheduleRetry(long backoffMillis, String error) {
        this.status = OutboxRecordStatus.NEW;
        this.nextAttemptAt = Instant.now().plusMillis(backoffMillis);
        this.lastError = truncate(error);
    }

    public void markPublished() {
        this.status = OutboxRecordStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void markDead(String error) {
        this.status = OutboxRecordStatus.DEAD;
        this.nextAttemptAt = Instant.now();
        this.lastError = truncate(error);
    }

    public boolean retryBudgetExhausted(int maxAttempts) {
        return attempts >= maxAttempts;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
