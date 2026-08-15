package com.orderplatform.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void shouldAddRequestIdHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").build());

        GatewayFilterChain chain = currentExchange -> {
            assertNotNull(currentExchange.getRequest().getHeaders().getFirst("X-Request-Id"));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
    }

    @Test
    void shouldHaveNegativeOrder() {
        assertEquals(-1, filter.getOrder());
    }

    @Test
    void shouldAddDifferentRequestIds() {
        MockServerWebExchange exchange1 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").build());
        MockServerWebExchange exchange2 = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").build());

        String[] id1 = new String[1];
        String[] id2 = new String[1];
        filter.filter(exchange1, e -> {
            id1[0] = e.getRequest().getHeaders().getFirst("X-Request-Id");
            return Mono.empty();
        }).block();
        filter.filter(exchange2, e -> {
            id2[0] = e.getRequest().getHeaders().getFirst("X-Request-Id");
            return Mono.empty();
        }).block();

        assertNotEquals(id1[0], id2[0]);
    }
}