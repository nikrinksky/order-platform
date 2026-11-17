package com.orderplatform.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Per-user rate limit on outgoing notifications, a token bucket kept in Redis.
 *
 * <p>A user who triggers a burst of order events - a scripted checkout, a retry loop of their
 * client, or a batch of status changes - would otherwise receive one email per event. The bucket
 * gives every user {@code capacity} messages at once and then one more per {@code refill-ms},
 * which keeps a normal order flow untouched while a burst is spread out in time.
 *
 * <p>The bucket lives in Redis so the limit is shared by all replicas of the service. Refill and
 * take are one Lua script, so two replicas cannot both see the last token as free.
 *
 * <p>Redis being unreachable fails open: notifications keep going out, only unlimited for the
 * duration of the outage. Losing mail because a cache is down would be the worse failure.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRateLimiter {

    /** Key prefix; the user id follows, so every user has a bucket of their own. */
    static final String KEY_PREFIX = "notification:rate-limit:";

    /**
     * Refills the bucket by the time elapsed since the last call and takes one token if there is
     * one. Run as a script so the read-modify-write cannot interleave with another replica.
     *
     * <p>KEYS[1] - bucket key; ARGV[1] - capacity; ARGV[2] - refill interval in ms;
     * ARGV[3] - current time in ms; ARGV[4] - key TTL in ms. Returns 1 when a token was taken.
     */
    private static final String ACQUIRE_SCRIPT_SOURCE = String.join("\n",
            "local key = KEYS[1]",
            "local capacity = tonumber(ARGV[1])",
            "local refill = tonumber(ARGV[2])",
            "local now = tonumber(ARGV[3])",
            "local ttl = tonumber(ARGV[4])",
            "local bucket = redis.call('HMGET', key, 'tokens', 'ts')",
            "local tokens = tonumber(bucket[1])",
            "local ts = tonumber(bucket[2])",
            "if tokens == nil then",
            "  tokens = capacity",
            "  ts = now",
            "end",
            "local elapsed = now - ts",
            "if elapsed < 0 then",
            "  elapsed = 0",
            "end",
            "local refilled = math.floor(elapsed / refill)",
            "if refilled > 0 then",
            "  tokens = math.min(capacity, tokens + refilled)",
            "  ts = ts + refilled * refill",
            "end",
            "local allowed = 0",
            "if tokens >= 1 then",
            "  tokens = tokens - 1",
            "  allowed = 1",
            "end",
            "redis.call('HSET', key, 'tokens', tokens, 'ts', ts)",
            "redis.call('PEXPIRE', key, ttl)",
            "return allowed");

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT =
            new DefaultRedisScript<>(ACQUIRE_SCRIPT_SOURCE, Long.class);

    private final StringRedisTemplate redisTemplate;

    /** Turns the limit off entirely; a deployment without Redis can run like this. */
    @Value("${notification.rate-limit.enabled:true}")
    private boolean enabled;

    /** How many notifications a user may receive back to back before the limit kicks in. */
    @Value("${notification.rate-limit.capacity:10}")
    private int capacity;

    /** How often one token comes back, i.e. the shortest gap between two messages. */
    @Value("${notification.rate-limit.refill-ms:60000}")
    private long refillMs;

    /**
     * Takes one token from the user's bucket.
     *
     * @param userId owner of the bucket; a missing id cannot be limited and is always allowed
     * @return true when the notification may be sent now
     */
    public boolean tryAcquire(String userId) {
        if (!enabled) {
            return true;
        }
        if (userId == null || userId.isBlank()) {
            // Nothing to attribute the message to, and one bucket shared by everyone would let a
            // single user silence the rest of the platform
            return true;
        }
        if (capacity < 1 || refillMs < 1) {
            // A zero capacity or refill would block every message, which is never the intent of a
            // misconfigured limit
            log.warn("Rate limit is misconfigured (capacity={}, refill-ms={}), notifications are not limited",
                    capacity, refillMs);
            return true;
        }

        long now = System.currentTimeMillis();
        // The bucket is full again after capacity * refill-ms; twice that keeps the key alive long
        // enough to be reused instead of letting it expire and hand out a fresh burst
        long ttl = capacity * refillMs * 2;
        try {
            Long allowed = redisTemplate.execute(ACQUIRE_SCRIPT, List.of(KEY_PREFIX + userId),
                    String.valueOf(capacity), String.valueOf(refillMs), String.valueOf(now),
                    String.valueOf(ttl));
            if (Long.valueOf(1L).equals(allowed)) {
                return true;
            }
            log.info("Rate limit reached for user {}, the notification is held back", userId);
            return false;
        } catch (RuntimeException e) {
            log.error("Rate limiter is unavailable, letting the notification through: {}", e.getMessage());
            return true;
        }
    }

    /**
     * How long a held-back notification waits for a token.
     *
     * @return the refill interval, or the one-minute default when the value is misconfigured
     */
    public long retryDelayMs() {
        return refillMs > 0 ? refillMs : 60000L;
    }
}
