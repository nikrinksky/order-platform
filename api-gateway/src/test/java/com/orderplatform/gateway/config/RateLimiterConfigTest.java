package com.orderplatform.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterConfigTest {

    private final RateLimiterConfig config = new RateLimiterConfig();

    @Test
    void shouldResolveUserFromAuthorizationHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header("Authorization", "Bearer token123")
                        .build());

        KeyResolver resolver = config.userKeyResolver();
        Mono<String> key = resolver.resolve(exchange);

        assertEquals("token123", key.block());
    }

    @Test
    void shouldReturnAnonymousWhenNoAuthorizationHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").build());

        KeyResolver resolver = config.userKeyResolver();
        Mono<String> key = resolver.resolve(exchange);

        assertEquals("anonymous", key.block());
    }
}