package com.orderplatform.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the claim query against a real database (H2), because the JPQL, the pessimistic lock and
 * the SKIP LOCKED hint are exactly the part mocks cannot check.
 */
@DataJpaTest
class OutboxRepositoryTest {

    @Autowired
    private OutboxRepository repository;

    private OutboxRecord record(String eventId, OutboxRecordStatus status, Instant nextAttemptAt) {
        return repository.save(OutboxRecord.builder()
                .eventId(eventId)
                .topic("order.created")
                .aggregateId("order-1")
                .eventType("OrderCreatedEvent")
                .payload("{}")
                .status(status)
                .attempts(0)
                .nextAttemptAt(nextAttemptAt)
                .createdAt(Instant.now())
                .build());
    }

    @Test
    void findDue_shouldReturnDueNewAndExpiredInFlightRecords() {
        Instant now = Instant.now();
        OutboxRecord dueNew = record("due-new", OutboxRecordStatus.NEW, now.minusSeconds(60));
        OutboxRecord expiredClaim = record("expired-claim", OutboxRecordStatus.IN_FLIGHT,
                now.minusSeconds(60));
        record("future", OutboxRecordStatus.NEW, now.plusSeconds(60));
        record("published", OutboxRecordStatus.PUBLISHED, now.minusSeconds(60));
        record("dead", OutboxRecordStatus.DEAD, now.minusSeconds(60));

        List<OutboxRecord> due = repository.findDue(
                List.of(OutboxRecordStatus.NEW, OutboxRecordStatus.IN_FLIGHT),
                now,
                PageRequest.of(0, 10));

        assertThat(due).extracting(OutboxRecord::getEventId)
                .containsExactlyInAnyOrder(dueNew.getEventId(), expiredClaim.getEventId());
    }

    @Test
    void findDue_shouldRespectBatchSize() {
        Instant past = Instant.now().minusSeconds(60);
        for (int i = 0; i < 5; i++) {
            record("record-" + i, OutboxRecordStatus.NEW, past);
        }

        // The reference instant is "now", clearly after the stored nextAttemptAt values:
        // comparing against the very same Instant makes the test depend on storage precision
        List<OutboxRecord> due = repository.findDue(
                List.of(OutboxRecordStatus.NEW), Instant.now(), PageRequest.of(0, 2));

        assertThat(due).hasSize(2);
    }

    @Test
    void findByEventId_shouldMatchStoredEventId() {
        OutboxRecord stored = record("order-1:CREATED", OutboxRecordStatus.NEW, Instant.now());

        assertThat(repository.findByEventId("order-1:CREATED")).contains(stored);
        assertThat(repository.findByEventId("missing")).isEmpty();
    }
}
