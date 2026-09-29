package com.orderplatform.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Access to the outbox table.
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxRecord, Long> {

    /**
     * Claims a batch of records that are due: never published ({@code NEW}) or left behind by a
     * relay whose claim has expired ({@code IN_FLIGHT}).
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} is what lets several replicas of the same service run a
     * relay at once: a row another replica holds is skipped instead of waited for, so the same
     * event is not published twice and a slow replica does not stall the rest. The {@code -2}
     * lock timeout is JPA's constant for "skip locked rows".
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select o from OutboxRecord o
            where o.status in :statuses and o.nextAttemptAt <= :now
            order by o.nextAttemptAt asc, o.createdAt asc
            """)
    List<OutboxRecord> findDue(@Param("statuses") Collection<OutboxRecordStatus> statuses,
                               @Param("now") Instant now,
                               Pageable pageable);

    Optional<OutboxRecord> findByEventId(String eventId);

    /**
     * Records parked as {@code DEAD}. Meant for an alert: a number that grows means events are
     * being given up on rather than delivered.
     */
    long countByStatus(OutboxRecordStatus status);
}
