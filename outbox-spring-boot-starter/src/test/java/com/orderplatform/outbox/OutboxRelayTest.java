package com.orderplatform.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxRepository repository;

    @Mock
    private OutboxPublisher publisher;

    @Mock
    private ObjectProvider<DeadLetterPublisher> deadLetterPublisher;

    private final OutboxProperties properties = new OutboxProperties();

    private OutboxRelay relay() {
        return new OutboxRelay(repository, publisher, deadLetterPublisher, properties,
                new NoopTransactionManager());
    }

    private OutboxRecord record(int attemptsSoFar) {
        OutboxRecord record = OutboxRecord.builder()
                .id(42L)
                .eventId("order-1:CREATED")
                .topic("order.created")
                .aggregateId("order-1")
                .eventType("OrderCreatedEvent")
                .payload("{}")
                .status(OutboxRecordStatus.NEW)
                .attempts(attemptsSoFar)
                .nextAttemptAt(Instant.now())
                .createdAt(Instant.now())
                .build();
        return record;
    }

    @SuppressWarnings("unchecked")
    private void dueRecords(OutboxRecord... records) {
        when(repository.findDue(any(Collection.class), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(records));
    }

    @Test
    void publishPending_shouldMarkPublishedOnSuccess() {
        OutboxRecord record = record(0);
        dueRecords(record);
        when(repository.findById(42L)).thenReturn(Optional.of(record));

        relay().publishPending();

        verify(publisher).publish(any(OutboxMessage.class));
        assertThat(record.getStatus()).isEqualTo(OutboxRecordStatus.PUBLISHED);
        // The claim burned one attempt before the publish succeeded
        assertThat(record.getAttempts()).isEqualTo(1);
    }

    @Test
    void publishPending_shouldScheduleRetryWithBackoffOnFailure() {
        OutboxRecord record = record(0);
        dueRecords(record);
        when(repository.findById(42L)).thenReturn(Optional.of(record));
        when(deadLetterPublisher.getIfAvailable()).thenReturn(null);
        org.mockito.Mockito.doThrow(new IllegalStateException("broker down"))
                .when(publisher).publish(any(OutboxMessage.class));

        relay().publishPending();

        // Back to NEW with a delay, so the next poll retries it instead of spinning
        assertThat(record.getStatus()).isEqualTo(OutboxRecordStatus.NEW);
        assertThat(record.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(record.getLastError()).contains("broker down");
    }

    @Test
    void publishPending_shouldParkOnDeadLetterWhenBudgetExhausted() {
        // One claim away from the total budget
        OutboxRecord record = record(properties.getRetry().getMaxTotalAttempts() - 1);
        dueRecords(record);
        when(repository.findById(42L)).thenReturn(Optional.of(record));
        org.mockito.Mockito.doThrow(new IllegalStateException("broker down"))
                .when(publisher).publish(any(OutboxMessage.class));
        DeadLetterPublisher deadLetter = mock(DeadLetterPublisher.class);
        when(deadLetterPublisher.getIfAvailable()).thenReturn(deadLetter);

        relay().publishPending();

        verify(deadLetter).send(any(OutboxMessage.class), anyString());
        assertThat(record.getStatus()).isEqualTo(OutboxRecordStatus.DEAD);
    }

    @Test
    void publishPending_shouldMarkDeadWithoutPublisherWhenBudgetExhausted() {
        OutboxRecord record = record(properties.getRetry().getMaxTotalAttempts() - 1);
        dueRecords(record);
        when(repository.findById(42L)).thenReturn(Optional.of(record));
        org.mockito.Mockito.doThrow(new IllegalStateException("broker down"))
                .when(publisher).publish(any(OutboxMessage.class));
        // No DLQ bean: the record must still be closed, not retried forever
        when(deadLetterPublisher.getIfAvailable()).thenReturn(null);

        relay().publishPending();

        assertThat(record.getStatus()).isEqualTo(OutboxRecordStatus.DEAD);
        verify(repository, never()).save(any());
    }

    @Test
    void publishPending_shouldKeepRecordWhenDeadLetterAlsoFails() {
        OutboxRecord record = record(properties.getRetry().getMaxTotalAttempts() - 1);
        dueRecords(record);
        when(repository.findById(42L)).thenReturn(Optional.of(record));
        org.mockito.Mockito.doThrow(new IllegalStateException("broker down"))
                .when(publisher).publish(any(OutboxMessage.class));
        DeadLetterPublisher deadLetter = mock(DeadLetterPublisher.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("dlq down"))
                .when(deadLetter).send(any(OutboxMessage.class), anyString());
        when(deadLetterPublisher.getIfAvailable()).thenReturn(deadLetter);

        relay().publishPending();

        // The DLQ is unreachable: keep the record for another pass instead of losing the event
        assertThat(record.getStatus()).isEqualTo(OutboxRecordStatus.NEW);
        assertThat(record.getLastError()).contains("dlq down");
    }

    @Test
    void publishPending_shouldKeepRetryingBetweenPollBudgetAndTotalBudget() {
        // The claim is the 4th attempt: past the per-poll budget (3) but far from the total
        // budget (10), so with a DLQ present the record goes back to the queue, not to the DLQ
        OutboxRecord record = record(properties.getRetry().getMaxAttempts());
        dueRecords(record);
        when(repository.findById(42L)).thenReturn(Optional.of(record));
        when(deadLetterPublisher.getIfAvailable()).thenReturn(mock(DeadLetterPublisher.class));
        org.mockito.Mockito.doThrow(new IllegalStateException("broker down"))
                .when(publisher).publish(any(OutboxMessage.class));

        relay().publishPending();

        assertThat(record.getStatus()).isEqualTo(OutboxRecordStatus.NEW);
        assertThat(record.getNextAttemptAt()).isAfter(Instant.now());
    }

    /** The relay only needs a transaction boundary, not a database, for these tests. */
    private static class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition)
                throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
        }
    }
}
