package com.orderplatform.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        final String requestId = UUID.randomUUID().toString();
        final ServerWebExchange withRequestId = exchange.mutate()
                .request(r -> r.header("X-Request-Id", requestId))
                .build();
        return chain.filter(withRequestId);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
