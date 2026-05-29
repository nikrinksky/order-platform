package com.orderplatform.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Добавить токен в черный список
     * @param token JWT токен
     * @param expirationMillis время жизни токена в миллисекундах
     */
    public void blacklistToken(String token, long expirationMillis) {
        try {
            String key = "blacklist:" + token;
            redisTemplate.opsForValue().set(key, "true", expirationMillis, TimeUnit.MILLISECONDS);
            log.info("Token blacklisted with TTL: {} seconds", expirationMillis / 1000);
        } catch (Exception e) {
            log.error("Failed to blacklist token: {}", e.getMessage());
            e.printStackTrace(); // Это для отладки
        }
    }

    /**
     * Проверить, находится ли токен в черном списке
     * @param token JWT токен
     * @return true если токен в черном списке
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
     * Удалить токен из черного списка (для тестов)
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