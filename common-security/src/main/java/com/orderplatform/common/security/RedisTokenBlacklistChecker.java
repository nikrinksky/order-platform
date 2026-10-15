package com.orderplatform.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Reads the token blacklist that auth-service writes to Redis.
 *
 * <p>Only reads: the revocation itself belongs to auth-service, this side just has to agree with
 * it on the key layout ({@code blacklist:<token>}).
 *
 * <p>Redis being unreachable is answered with "not revoked" and a warning. Failing the other way
 * would turn a Redis outage into a total authentication outage for every service, and the window
 * in which a revoked token still works is bounded by the access token lifetime.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisTokenBlacklistChecker implements TokenBlacklistChecker {

    /** Key prefix of a blacklist entry, kept in sync with auth-service's TokenBlacklistService. */
    static final String KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
        } catch (Exception e) {
            log.warn("Cannot reach the token blacklist, treating the token as valid: {}",
                    e.getMessage());
            return false;
        }
    }
}
