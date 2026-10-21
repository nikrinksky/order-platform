package com.orderplatform.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Decides what the gateway rate limiter counts against.
 *
 * <p>The key is the authenticated user, not the access token. A token is a credential, not an
 * identity: it is reissued on every refresh, so a limiter keyed on it hands out a fresh bucket
 * whenever the client refreshes - the limit is trivially bypassed and every token becomes a new
 * entry in Redis.
 *
 * <p>Anonymous callers are counted per client address. They used to share the single literal
 * {@code anonymous} bucket, which meant one client could exhaust the budget of every public route
 * (the catalogue GETs, login, register) for everybody else.
 *
 * <p>{@code X-User-Id} is trusted here because {@link com.orderplatform.gateway.filter.JwtAuthGlobalFilter}
 * overwrites it for authenticated requests and strips any client-supplied value on the public ones.
 */
@Configuration
public class RateLimiterConfig {

    /** Header the gateway sets from the validated token and never forwards from the client. */
    static final String USER_ID_HEADER = "X-User-Id";

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.just(keyFor(exchange));
    }

    private static String keyFor(ServerWebExchange exchange) {
        final String userId = exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER);
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
        }
        return "ip:" + clientAddress(exchange);
    }

    /**
     * Address of the caller. {@code X-Forwarded-For} wins because the gateway normally sits behind
     * a proxy or a load balancer, where the socket address is the proxy's and would put every
     * anonymous caller into one bucket again.
     */
    private static String clientAddress(ServerWebExchange exchange) {
        final String forwardedFor = exchange.getRequest().getHeaders().getFirst(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // The header is a list; the first entry is the original client.
            final int comma = forwardedFor.indexOf(',');
            return (comma > 0 ? forwardedFor.substring(0, comma) : forwardedFor).trim();
        }
        final InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        return remote.getAddress().getHostAddress();
    }
}
