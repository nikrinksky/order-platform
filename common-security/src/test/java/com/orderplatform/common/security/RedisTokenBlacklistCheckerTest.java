package com.orderplatform.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistCheckerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisTokenBlacklistChecker checker;

    @BeforeEach
    void setUp() {
        checker = new RedisTokenBlacklistChecker(redisTemplate);
    }

    @Test
    void shouldReportRevokedToken() {
        when(redisTemplate.hasKey("blacklist:revoked")).thenReturn(true);

        assertThat(checker.isBlacklisted("revoked")).isTrue();
    }

    @Test
    void shouldReportTokenThatWasNeverRevoked() {
        when(redisTemplate.hasKey("blacklist:fresh")).thenReturn(false);

        assertThat(checker.isBlacklisted("fresh")).isFalse();
    }

    /**
     * An unreachable Redis must not fail authentication for every service at once: the token is
     * accepted and the window is bounded by the access token lifetime.
     */
    @Test
    void shouldTreatTokenAsValidWhenRedisIsUnreachable() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis is down"));

        assertThat(checker.isBlacklisted("any-token")).isFalse();
    }

    /**
     * The lookup has to use the same key layout auth-service writes on logout.
     */
    @Test
    void shouldLookUpTheKeyAuthServiceWrites() {
        when(redisTemplate.hasKey("blacklist:some.jwt.token")).thenReturn(true);

        checker.isBlacklisted("some.jwt.token");

        org.mockito.Mockito.verify(redisTemplate).hasKey("blacklist:some.jwt.token");
    }
}
