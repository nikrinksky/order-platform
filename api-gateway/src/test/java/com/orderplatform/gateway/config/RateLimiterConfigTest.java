package com.orderplatform.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterConfigTest {

    private final RateLimiterConfig config = new RateLimiterConfig();

    private String keyFor(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request.build());
        KeyResolver resolver = config.userKeyResolver();
        Mono<String> key = resolver.resolve(exchange);
        return key.block();
    }

    /** A request that really arrived from the given peer address. */
    private static MockServerHttpRequest.BaseBuilder<?> requestFrom(String path, String peer) {
        return MockServerHttpRequest.get(path)
                .remoteAddress(new InetSocketAddress(peer, 51234));
    }

    private void trust(String proxies) {
        ReflectionTestUtils.setField(config, "trustedProxies", proxies);
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
                keyFor(requestFrom("/api/products", "203.0.113.7")));
    }

    @Test
    void shouldGiveDifferentAnonymousCallersDifferentBuckets() {
        String first = keyFor(requestFrom("/api/products", "203.0.113.7"));
        String second = keyFor(requestFrom("/api/products", "198.51.100.9"));

        assertNotEquals(first, second);
    }

    /**
     * The defect this guards: the gateway port is published directly, so the header was whatever
     * the caller wrote - one client handed itself a fresh bucket on every request.
     */
    @Test
    void shouldIgnoreForwardedForFromAnUntrustedPeer() {
        assertEquals("ip:203.0.113.7",
                keyFor(requestFrom("/api/products", "203.0.113.7")
                        .header("X-Forwarded-For", "198.51.100.9")));
    }

    @Test
    void shouldIgnoreForwardedForWhenNoProxyIsTrusted() {
        trust("");

        assertEquals("ip:10.0.0.1",
                keyFor(requestFrom("/api/products", "10.0.0.1")
                        .header("X-Forwarded-For", "203.0.113.7")));
    }

    /** Behind a configured proxy the header is the only way to see the original client. */
    @Test
    void shouldTakeTheClientAddressFromATrustedProxy() {
        trust("10.0.0.1");

        assertEquals("ip:203.0.113.7",
                keyFor(requestFrom("/api/products", "10.0.0.1")
                        .header("X-Forwarded-For", "203.0.113.7")));
    }

    /**
     * A chain is walked from the right: the leftmost entry is what the client claimed, the
     * rightmost untrusted address is the one the nearest proxy actually saw.
     */
    @Test
    void shouldSkipTrustedHopsAndTakeTheNearestUntrustedAddress() {
        trust("10.0.0.1, 10.0.0.2");

        assertEquals("ip:203.0.113.7",
                keyFor(requestFrom("/api/products", "10.0.0.1")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.2")));
    }

    /** A client that prepends its own value cannot hide behind the real address the proxy appended. */
    @Test
    void shouldNotLetATrustedProxyClientSpoofItsOwnEntry() {
        trust("10.0.0.1");

        assertEquals("ip:198.51.100.9",
                keyFor(requestFrom("/api/products", "10.0.0.1")
                        .header("X-Forwarded-For", "1.2.3.4, 198.51.100.9")));
    }

    /** Every hop trusted means there is no client address to report; the peer is used. */
    @Test
    void shouldFallBackToThePeerWhenEveryHopIsTrusted() {
        trust("10.0.0.1, 10.0.0.2");

        assertEquals("ip:10.0.0.1",
                keyFor(requestFrom("/api/products", "10.0.0.1")
                        .header("X-Forwarded-For", "10.0.0.2")));
    }

    @Test
    void shouldFallBackToUnknownWithoutAnyAddressInformation() {
        assertEquals("ip:unknown",
                keyFor(MockServerHttpRequest.get("/api/products")
                        .remoteAddress(null)));
    }

    /**
     * A blank identity header is not an identity: treating it as one would let a client without a
     * token slip into the same bucket as everybody else sending a blank value.
     */
    @Test
    void shouldIgnoreBlankUserIdHeader() {
        assertEquals("ip:unknown",
                keyFor(MockServerHttpRequest.get("/api/products")
                        .remoteAddress(null)
                        .header(RateLimiterConfig.USER_ID_HEADER, "   ")));
    }
}