/**
 * Service for token blacklisting.
 * Uses Redis to store blacklisted tokens with TTL.
 */
package com.orderplatform.auth.service;

import com.orderplatform.auth.exception.TokenRevocationUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for token blacklisting.
 * Uses Redis to store blacklisted tokens with TTL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Adds a token to the blacklist.
     *
     * <p>Writing is fail-closed: a revocation that did not reach Redis is reported, not swallowed.
     * The caller answers {@code 503}, because a logout that returns success while the token is
     * still usable is worse than a visible outage - the client would stop trying and keep a token
     * it believes dead. Reading stays fail-open, see {@link #isTokenBlacklisted(String)}.
     *
     * @param token JWT token to blacklist
     * @param ttlSeconds token time-to-live in seconds
     * @throws TokenRevocationUnavailableException when the entry could not be written
     */
    public void blacklistToken(String token, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            // Nothing to protect: the token is rejected by the expiry check before the blacklist
            log.debug("Token TTL is 0 or negative, no blacklist entry needed");
            return;
        }
        String key = "blacklist:" + token;
        try {
            redisTemplate.opsForValue().set(key, "true", ttlSeconds, TimeUnit.SECONDS);
            log.info("Token blacklisted with TTL: {} seconds", ttlSeconds);
        } catch (Exception e) {
            log.error("Failed to blacklist token, the token stays usable: {}", e.getMessage());
            throw new TokenRevocationUnavailableException("Token revocation is unavailable", e);
        }
    }

    /**
     * Checks if a token is in the blacklist.
     *
     * <p>Reading is fail-open: an unreachable Redis is answered with "not revoked". Failing the
     * other way would turn a Redis outage into a total authentication outage, and the window in
     * which a revoked token still works is bounded by its remaining lifetime.
     *
     * @param token JWT token to check
     * @return true if token is blacklisted
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            String key = "blacklist:" + token;
            // Unboxed on purpose: a null answer would throw, and the catch below answers "not
            // revoked" - which is exactly the fail-open behaviour this read wants
            boolean isBlacklisted = redisTemplate.hasKey(key);
            if (isBlacklisted) {
                log.info("Token is blacklisted: {}...", token.substring(0, Math.min(50, token.length())));
            }
            return isBlacklisted;
        } catch (Exception e) {
            log.error("Failed to check blacklist, treating the token as valid: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Removes a token from the blacklist (for testing purposes).
     *
     * @param token JWT token to remove
     */
    public void removeFromBlacklist(String token) {
        try {
            String key = "blacklist:" + token;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Failed to remove from blacklist: {}", e.getMessage());
        }
    }
}
