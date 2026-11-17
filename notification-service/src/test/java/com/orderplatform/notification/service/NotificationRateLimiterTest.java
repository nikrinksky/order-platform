package com.orderplatform.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private NotificationRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new NotificationRateLimiter(redisTemplate);
        ReflectionTestUtils.setField(limiter, "enabled", true);
        ReflectionTestUtils.setField(limiter, "capacity", 3);
        ReflectionTestUtils.setField(limiter, "refillMs", 1000L);
    }

    @Test
    void tryAcquire_shouldTakeATokenWhenTheBucketHasRoom() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(1L);

        assertTrue(limiter.tryAcquire("user-1"));
    }

    @Test
    void tryAcquire_shouldRefuseWhenTheBucketIsEmpty() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(0L);

        assertFalse(limiter.tryAcquire("user-1"));
    }

    /** An empty answer is not a token: the script always returns a number, so this is a refusal. */
    @Test
    void tryAcquire_shouldRefuseWhenRedisReturnsNoValue() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(null);

        assertFalse(limiter.tryAcquire("user-1"));
    }

    /** A Redis outage must not stop the notifications: they go out unlimited instead. */
    @Test
    void tryAcquire_shouldFailOpenWhenRedisIsUnavailable() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("Redis is down"));

        assertTrue(limiter.tryAcquire("user-1"));
    }

    @Test
    void tryAcquire_shouldAllowEverythingWhenDisabled() {
        ReflectionTestUtils.setField(limiter, "enabled", false);

        assertTrue(limiter.tryAcquire("user-1"));
        verifyNoInteractions(redisTemplate);
    }

    /** Without a user id there is no bucket to take a token from. */
    @Test
    void tryAcquire_shouldSkipTheBucketForAnUnknownUser() {
        assertTrue(limiter.tryAcquire(null));
        assertTrue(limiter.tryAcquire(" "));
        verifyNoInteractions(redisTemplate);
    }

    /** A zero capacity or refill would silence every user, so it is treated as "no limit". */
    @Test
    void tryAcquire_shouldNotBlockWhenTheLimitIsMisconfigured() {
        ReflectionTestUtils.setField(limiter, "capacity", 0);

        assertTrue(limiter.tryAcquire("user-1"));
        verifyNoInteractions(redisTemplate);
    }

    /** Every user needs a bucket of their own, otherwise one burst would silence the platform. */
    @Test
    void tryAcquire_shouldUseAKeyPerUser() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(1L);

        limiter.tryAcquire("user-42");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(),
                anyString(), anyString(), anyString(), anyString());
        assertEquals(List.of(NotificationRateLimiter.KEY_PREFIX + "user-42"), keys.getValue());
    }

    @Test
    void retryDelayMs_shouldBeTheRefillInterval() {
        assertEquals(1000L, limiter.retryDelayMs());
    }

    @Test
    void retryDelayMs_shouldFallBackToTheDefaultWhenTheRefillIsMisconfigured() {
        ReflectionTestUtils.setField(limiter, "refillMs", 0L);

        assertEquals(60000L, limiter.retryDelayMs());
    }
}
