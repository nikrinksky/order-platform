package com.orderplatform.auth;

import com.orderplatform.auth.service.TokenBlacklistService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test implementation of TokenBlacklistService that tracks blacklisted tokens.
 * Simulates Redis-based blacklisting in integration tests where Redis is not available.
 */
@Service
@Primary
public class TestTokenBlacklistService extends TokenBlacklistService {

    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    public TestTokenBlacklistService() {
        super(null);
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        return blacklistedTokens.contains(token);
    }

    @Override
    public void blacklistToken(String token, long ttlSeconds) {
        blacklistedTokens.add(token);
    }

    @Override
    public void removeFromBlacklist(String token) {
        blacklistedTokens.remove(token);
    }
}
