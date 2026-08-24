package com.orderplatform.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Key;
import java.util.Date;

/**
 * Validates the JWT access token on requests routed to protected services.
 * <p>
 * Public endpoints (auth login/register, product GET) are whitelisted.
 * All other routes require a valid Bearer access token.
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    /** Claim name for token type. */
    private static final String TOKEN_TYPE_CLAIM = "token_use";

    /** Claim name for user ID. */
    private static final String USER_ID_CLAIM = "userId";

    /** Claim name for user roles. */
    private static final String ROLES_CLAIM = "roles";

    /** Prefix length of "Bearer " header value. */
    private static final int BEARER_PREFIX_LENGTH = 7;

    @Value("${jwt.secret}")
    private String secret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Block internal-only endpoints from external access
        if (isInternalPath(path)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // Public endpoints - no token required
        if (isPublicPath(path, exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(BEARER_PREFIX_LENGTH);

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSignInKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            if (!"access".equals(claims.get(TOKEN_TYPE_CLAIM, String.class))
                    || claims.getExpiration().before(new Date())) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String userId = claims.get(USER_ID_CLAIM, String.class);
            Object rolesObj = claims.get(ROLES_CLAIM);
            String roles = rolesObj != null ? String.join(",", (java.util.List<String>) rolesObj) : "";

            // Forward identity to downstream services
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Roles", roles)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isPublicPath(String path, String method) {
        if (path.startsWith("/fallback")) {
            return true;
        }
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/refresh")) {
            return true;
        }
        if (path.startsWith("/actuator")) {
            return true;
        }
        return "GET".equals(method) && path.startsWith("/api/products");
    }

    /**
     * Internal-only endpoints that must not be accessible from outside the cluster.
     * These are called service-to-service (e.g. order-service -> inventory-service).
     */
    private boolean isInternalPath(String path) {
        return path.startsWith("/api/inventory/reserve")
                || path.startsWith("/api/inventory/release");
    }

    private Key getSignInKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
