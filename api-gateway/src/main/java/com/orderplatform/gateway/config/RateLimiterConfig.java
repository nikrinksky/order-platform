package com.orderplatform.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Addresses of the proxies whose {@code X-Forwarded-For} is believed.
     *
     * <p>Empty by default, and that is the safe default: the gateway port is published directly, so
     * a header coming from an arbitrary peer proves nothing - the client would simply write itself
     * a new bucket on every request and the limiter would count nothing. The list is filled in a
     * deployment where the gateway sits behind a known proxy or load balancer.
     */
    @Value("${gateway.trusted-proxies:}")
    private String trustedProxies = "";

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.just(keyFor(exchange));
    }

    private String keyFor(ServerWebExchange exchange) {
        final String userId = exchange.getRequest().getHeaders().getFirst(USER_ID_HEADER);
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
        }
        return "ip:" + clientAddress(exchange);
    }

    /**
     * Address of the caller.
     *
     * <p>{@code X-Forwarded-For} is read only when the request actually arrived from a trusted
     * proxy: otherwise the peer address is the caller, and the header is a value the caller chose
     * for itself. With a trusted peer the chain is walked from the right, skipping trusted hops -
     * the leftmost entry is whatever the client claimed, while the rightmost untrusted address is
     * the one the nearest proxy saw.
     */
    private String clientAddress(ServerWebExchange exchange) {
        final String peer = peerAddress(exchange);
        final List<String> forwarded = forwardedFor(exchange);

        if (forwarded.isEmpty() || !isTrusted(peer)) {
            return peer;
        }

        for (int i = forwarded.size() - 1; i >= 0; i--) {
            final String hop = forwarded.get(i);
            if (!isTrusted(hop)) {
                return hop;
            }
        }
        // Every hop named a trusted proxy: the peer itself is the closest thing to a client
        return peer;
    }

    /** Socket address of the peer, or {@code unknown} when the transport did not report one. */
    private static String peerAddress(ServerWebExchange exchange) {
        final InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        return remote.getAddress().getHostAddress();
    }

    /** The forwarded chain as a list of addresses, empty when the header is absent or blank. */
    private static List<String> forwardedFor(ServerWebExchange exchange) {
        final String header = exchange.getRequest().getHeaders().getFirst(FORWARDED_FOR_HEADER);
        if (header == null || header.isBlank()) {
            return List.of();
        }
        final List<String> addresses = new ArrayList<>();
        for (String part : header.split(",")) {
            final String address = part.trim();
            if (!address.isEmpty()) {
                addresses.add(address);
            }
        }
        return addresses;
    }

    /**
     * Tells whether an address belongs to a configured proxy.
     *
     * @param address address to check, e.g. {@code 10.0.0.1}
     * @return true when the address is listed in {@code gateway.trusted-proxies}
     */
    private boolean isTrusted(String address) {
        for (String entry : trustedProxies.split(",")) {
            if (entry.trim().equals(address)) {
                return true;
            }
        }
        return false;
    }
}
