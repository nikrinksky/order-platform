package com.orderplatform.common.security;

/**
 * Answers whether a token has been revoked.
 *
 * <p>Tokens are revoked by auth-service on logout and on refresh, so every service that accepts
 * one has to consult the same blacklist. Without this check a logged out token stayed valid until
 * it expired, which made logout cosmetic on all the downstream services.
 */
public interface TokenBlacklistChecker {

    /**
     * Checks whether the token is revoked.
     *
     * @param token the access token to look up
     * @return true when the token must be rejected
     */
    boolean isBlacklisted(String token);
}
