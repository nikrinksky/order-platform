/**
 * Service for token blacklisting.
 * Uses Redis to store blacklisted tokens with TTL.
 */
package com.orderplatform.auth.service;

import com.orderplatform.auth.AuthConstants;
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
     * @param token JWT token to blacklist
     * @param expirationMillis token expiration time in milliseconds
     */
    public void blacklistToken(String token, long expirationMillis) {
        try {
            String key = "blacklist:" + token;
            redisTemplate.opsForValue().set(key, "true", expirationMillis, TimeUnit.MILLISECONDS);
            long seconds = expirationMillis / AuthConstants.MILLISECONDS_PER_SECOND;
            log.info("Token blacklisted with TTL: {} seconds", seconds);
        } catch (Exception e) {
            log.error("Failed to blacklist token: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Checks if a token is in the blacklist.
     *
     * @param token JWT token to check
     * @return true if token is blacklisted
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            String key = "blacklist:" + token;
            Boolean isBlacklisted = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(isBlacklisted);
        } catch (Exception e) {
            log.error("Failed to check blacklist: {}", e.getMessage());
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
