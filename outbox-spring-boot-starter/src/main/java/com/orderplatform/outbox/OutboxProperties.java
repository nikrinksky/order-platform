package com.orderplatform.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings of the outbox relay, bound from the {@code outbox.*} prefix.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "outbox")
public class OutboxProperties {

    /** Master switch for the relay; appending still works so events are not lost while off. */
    private boolean enabled = true;

    /** Topic suffix used for events whose retry budget is exhausted. */
    private String deadLetterSuffix = ".DLT";

    /** Schema name to event class, e.g. {@code OrderCreatedEvent=com.orderplatform...OrderCreatedEvent}. */
    private Map<String, String> events = new LinkedHashMap<>();

    private final Retry retry = new Retry();
    private final Relay relay = new Relay();

    /**
     * Retry budget of the relay. The backoff is deliberately short: a failed publish is
     * rescheduled in the database and retried on the next poll, so the relay must not sit in
     * long sleeps holding a database transaction.
     */
    @Getter
    @Setter
    public static class Retry {

        /** In-process attempts on the publisher before the record goes back for another poll. */
        private int maxAttempts = 3;

        /** Delay before the second attempt; every later one is multiplied by {@code multiplier}. */
        private long initialBackoffMs = 200;

        /** Ceiling of the exponential growth, so attempt 20 does not sleep for hours. */
        private long maxBackoffMs = 2_000;

        private double multiplier = 2.0;

        /**
         * Random spread applied to the computed delay, {@code 0.2} meaning plus or minus twenty
         * percent. Without it every replica retries the same stuck record in the same instant.
         */
        private double jitter = 0.2;

        /** Publishes of one record before it is parked on the dead-letter topic. */
        private int maxTotalAttempts = 10;
    }

    @Getter
    @Setter
    public static class Relay {

        /** How many records one poll claims. */
        private int batchSize = 100;

        /**
         * How long a claimed record stays invisible to other replicas. It must exceed the time a
         * healthy publish takes, otherwise two replicas publish the same event.
         */
        private long claimTimeoutMs = 60_000;

        /** Interval used while records are pending; the schedule itself is fixed-delay. */
        private long pollIntervalMs = 1_000;

        /**
         * How long the publisher waits for the broker's acknowledgement. Bounded below the claim
         * timeout, otherwise the claim expires while the record is still in flight.
         */
        private long sendTimeoutMs = 10_000;
    }
}
