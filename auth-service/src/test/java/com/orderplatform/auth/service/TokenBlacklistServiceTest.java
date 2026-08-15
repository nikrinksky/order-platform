package com.orderplatform.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenBlacklistService service;

    @Test
    void blacklistToken_shouldStoreWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.blacklistToken("token-123", 3600);

        verify(valueOperations).set("blacklist:token-123", "true", 3600, TimeUnit.SECONDS);
    }

    @Test
    void blacklistToken_shouldSkipNonPositiveTtl() {
        service.blacklistToken("token-123", 0);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void isTokenBlacklisted_shouldReturnTrueWhenExists() {
        when(redisTemplate.hasKey("blacklist:token-123")).thenReturn(true);

        assertTrue(service.isTokenBlacklisted("token-123"));
    }

    @Test
    void isTokenBlacklisted_shouldReturnFalseWhenNotExists() {
        when(redisTemplate.hasKey("blacklist:token-123")).thenReturn(false);

        assertFalse(service.isTokenBlacklisted("token-123"));
    }

    @Test
    void isTokenBlacklisted_shouldReturnFalseOnException() {
        when(redisTemplate.hasKey("blacklist:token-123")).thenThrow(new RuntimeException("Redis down"));

        assertFalse(service.isTokenBlacklisted("token-123"));
    }

    @Test
    void removeFromBlacklist_shouldDeleteKey() {
        service.removeFromBlacklist("token-123");

        verify(redisTemplate).delete("blacklist:token-123");
    }
}