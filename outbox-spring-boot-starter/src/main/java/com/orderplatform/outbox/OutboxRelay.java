package com.orderplatform.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Moves persisted outbox records onto the message broker.
 *
 * <p>Each pass claims a batch in its own short transaction, releases it, and only then publishes.
 * Publishing never happens while a database row is locked: a broker that hangs for a minute must
 * not hold row locks and stall the writers that append new events.
 *
 * <p>A claim that is not completed within {@code relay.claim-timeout-ms} becomes visible again, so
 * a relay instance that dies mid batch does not strand its records in {@code IN_FLIGHT}.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final OutboxPublisher publisher;
    private final ObjectProvider<DeadLetterPublisher> deadLetterPublisher;
    private final OutboxProperties properties;
    private final TransactionOperations claimOperations;

    public OutboxRelay(OutboxRepository repository,
                       OutboxPublisher publisher,
                       ObjectProvider<DeadLetterPublisher> deadLetterPublisher,
                       OutboxProperties properties,
                       PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.publisher = publisher;
        this.deadLetterPublisher = deadLetterPublisher;
        this.properties = properties;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.claimOperations = template;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:1000}")
    public void publishPending() {
        List<OutboxClaim> batch = claimBatch();
        for (OutboxClaim claim : batch) {
            publishOne(claim);
        }
    }

    /**
     * Reserves up to {@code relay.batch-size} due records. The claim is committed before this
     * method returns, which is what makes the publish phase lock free.
     */
    private List<OutboxClaim> claimBatch() {
        OutboxProperties.Relay relay = properties.getRelay();
        return claimOperations.execute(status -> {
            List<OutboxRecord> due = repository.findDue(
                    List.of(OutboxRecordStatus.NEW, OutboxRecordStatus.IN_FLIGHT),
                    Instant.now(),
                    PageRequest.of(0, relay.getBatchSize()));
            due.forEach(record -> record.claim(relay.getClaimTimeoutMs()));
            repository.saveAll(due);
            return due.stream()
                    .map(record -> new OutboxClaim(record.getId(), toMessage(record), record.getAttempts()))
                    .toList();
        });
    }

    private void publishOne(OutboxClaim claim) {
        try {
            publisher.publish(claim.message());
            claimOperations.executeWithoutResult(status -> repository.findById(claim.id())
                    .ifPresent(OutboxRecord::markPublished));
        } catch (Exception e) {
            log.warn("Outbox record {} (topic {}) failed on attempt {}",
                    claim.id(), claim.message().getTopic(), claim.attempts(), e);
            handleFailure(claim, e);
        }
    }

    private void handleFailure(OutboxClaim claim, Exception cause) {
        OutboxProperties.Retry retry = properties.getRetry();
        DeadLetterPublisher deadLetter = deadLetterPublisher.getIfAvailable();

        if (claim.attempts() >= retry.getMaxTotalAttempts() || deadLetter == null
                && claim.attempts() >= retry.getMaxAttempts()) {
            deadLetterRecord(claim, cause, deadLetter);
            return;
        }
        long backoff = backoffMillis(claim.attempts(), retry);
        claimOperations.executeWithoutResult(status -> repository.findById(claim.id())
                .ifPresent(record -> record.scheduleRetry(backoff, describe(cause))));
    }

    private void deadLetterRecord(OutboxClaim claim, Exception cause, DeadLetterPublisher deadLetter) {
        if (deadLetter != null) {
            try {
                deadLetter.send(claim.message(), describe(cause));
            } catch (Exception dlqFailure) {
                // The dead letter topic is unreachable. Keep the record for another pass instead of
                // dropping the event; it will keep retrying until the DLQ or the broker recovers.
                log.error("Dead letter delivery failed for outbox record {}, keeping it for retry",
                        claim.id(), dlqFailure);
                long backoff = backoffMillis(claim.attempts(), properties.getRetry());
                claimOperations.executeWithoutResult(status -> repository.findById(claim.id())
                        .ifPresent(record -> record.scheduleRetry(backoff, describe(dlqFailure))));
                return;
            }
        }
        claimOperations.executeWithoutResult(status -> repository.findById(claim.id())
                .ifPresent(record -> record.markDead(describe(cause))));
        log.error("Outbox record {} (topic {}) exhausted its retries and is now DEAD",
                claim.id(), claim.message().getTopic());
    }

    /**
     * Exponential delay with jitter. The attempt counter is the one stored on the record, so a
     * restart of the relay does not reset the growth.
     */
    private long backoffMillis(long attempt, OutboxProperties.Retry retry) {
        double exponential = retry.getInitialBackoffMs()
                * Math.pow(retry.getMultiplier(), Math.max(0, attempt - 1));
        long capped = (long) Math.min(exponential, retry.getMaxBackoffMs());
        double jitter = 1.0 + (Math.random() * 2 - 1) * retry.getJitter();
        return Math.max(1, (long) (capped * jitter));
    }

    private OutboxMessage toMessage(OutboxRecord record) {
        return new OutboxMessage(record.getId(), record.getTopic(), record.getAggregateId(),
                record.getEventType(), record.getPayload(), record.getAttempts());
    }

    private String describe(Exception cause) {
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    /**
     * Snapshot of a claimed record, detached on purpose: the publish phase must not touch the
     * session that claimed it.
     */
    private record OutboxClaim(Long id, OutboxMessage message, long attempts) {
    }
}
