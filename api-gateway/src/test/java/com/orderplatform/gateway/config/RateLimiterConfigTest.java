package com.orderplatform.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterConfigTest {

    private final RateLimiterConfig config = new RateLimiterConfig();

    private String keyFor(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request.build());
        KeyResolver resolver = config.userKeyResolver();
        Mono<String> key = resolver.resolve(exchange);
        return key.block();
    }

    /**
     * The bucket belongs to the user, not to the token: a token is reissued on every refresh, so
     * counting per token would hand out a fresh bucket each time the client refreshes.
     */
    @Test
    void shouldResolveKeyFromForwardedUserId() {
        assertEquals("user:user-42",
                keyFor(MockServerHttpRequest.get("/api/orders")
                        .header(RateLimiterConfig.USER_ID_HEADER, "user-42")));
    }

    @Test
    void shouldUseTheSameBucketForTwoDifferentTokensOfOneUser() {
        String first = keyFor(MockServerHttpRequest.get("/api/orders")
                .header(RateLimiterConfig.USER_ID_HEADER, "user-42")
                .header("Authorization", "Bearer token-one"));
        String second = keyFor(MockServerHttpRequest.get("/api/orders")
                .header(RateLimiterConfig.USER_ID_HEADER, "user-42")
                .header("Authorization", "Bearer token-two"));

        assertEquals(first, second);
    }

    /**
     * Anonymous callers used to share the single literal {@code anonymous} bucket, so one client
     * could spend the budget of every public route for everybody.
     */
    @Test
    void shouldCountAnonymousCallersPerAddress() {
        assertEquals("ip:203.0.113.7",
                keyFor(MockServerHttpRequest.get("/api/products")
                        .header("X-Forwarded-For", "203.0.113.7")));
    }

    @Test
    void shouldGiveDifferentAnonymousCallersDifferentBuckets() {
        String first = keyFor(MockServerHttpRequest.get("/api/products")
                .header("X-Forwarded-For", "203.0.113.7"));
        String second = keyFor(MockServerHttpRequest.get("/api/products")
                .header("X-Forwarded-For", "198.51.100.9"));

        assertNotEquals(first, second);
    }

    /** A proxy chain lists the original client first; the rest are intermediaries. */
    @Test
    void shouldTakeTheFirstAddressOfAForwardedForChain() {
        assertEquals("ip:203.0.113.7",
                keyFor(MockServerHttpRequest.get("/api/products")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1, 10.0.0.2")));
    }

    @Test
    void shouldFallBackToUnknownWithoutAnyAddressInformation() {
        assertEquals("ip:unknown",
                keyFor(MockServerHttpRequest.get("/api/products")));
    }

    /**
     * A blank identity header is not an identity: treating it as one would let a client without a
     * token slip into the same bucket as everybody else sending a blank value.
     */
    @Test
    void shouldIgnoreBlankUserIdHeader() {
        assertEquals("ip:unknown",
                keyFor(MockServerHttpRequest.get("/api/products")
                        .header(RateLimiterConfig.USER_ID_HEADER, "   ")));
    }
}