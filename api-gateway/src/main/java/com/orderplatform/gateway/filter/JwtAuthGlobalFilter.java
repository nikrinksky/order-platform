package com.orderplatform.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
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

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    /** Claim name for token type. */
    private static final String TOKEN_TYPE_CLAIM = "token_use";

    /** Claim name for user ID. */
    private static final String USER_ID_CLAIM = "userId";

    /** Claim name for user roles. */
    private static final String ROLES_CLAIM = "roles";

    /** Prefix length of "Bearer " header value. */
    private static final int BEARER_PREFIX_LENGTH = 7;

    /** Runs before the routing and rate limiting filters, so the identity is known to both. */
    private static final int AUTH_FILTER_ORDER = -100;

    /** Key prefix of a blacklist entry, kept in sync with auth-service. */
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:";

    /** Identity headers the gateway owns; a client-supplied value is never forwarded. */
    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String USER_ROLES_HEADER = "X-User-Roles";

    @Value("${jwt.secret}")
    private String secret;

    /**
     * Optional: absent in a unit test or a deployment without Redis. When present, a token revoked
     * on logout is rejected here as well, so the gateway does not let it through for the rest of
     * its lifetime.
     */
    @Autowired(required = false)
    private ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        final String path = exchange.getRequest().getURI().getPath();

        // Block internal-only endpoints from external access
        if (isInternalPath(path)) {
            return reject(exchange, HttpStatus.FORBIDDEN);
        }

        // Public endpoints - no token required. The identity headers are stripped rather than
        // forwarded: without a validated token there is nothing to vouch for them, and a client
        // could otherwise claim to be any user and be counted under that user's rate limit.
        if (isPublicPath(path, exchange.getRequest().getMethod().name())) {
            return chain.filter(stripIdentityHeaders(exchange));
        }

        final String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        final String token = authHeader.substring(BEARER_PREFIX_LENGTH);
        final Identity identity = parseIdentity(token, path);
        if (identity == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        return isRevoked(token).flatMap(revoked -> {
            if (revoked) {
                log.warn("Rejected a revoked access token on {}", path);
                return reject(exchange, HttpStatus.UNAUTHORIZED);
            }

            // Forward identity to downstream services
            final ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header(USER_ID_HEADER, identity.userId())
                    .header(USER_ROLES_HEADER, identity.roles())
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        });
    }

    /**
     * Reads the identity out of the token.
     *
     * <p>Returns null instead of throwing so the caller has one place to answer with 401; the
     * token is untrusted input, and a malformed or expired one is a rejection, not an error.
     *
     * @param token the raw access token
     * @param path  request path, used for the log line
     * @return the identity carried by the token, or null when the token must be rejected
     */
    @SuppressWarnings("unchecked")
    private Identity parseIdentity(String token, String path) {
        try {
            final Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSignInKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            if (!"access".equals(claims.get(TOKEN_TYPE_CLAIM, String.class)) ||
                    claims.getExpiration().before(new Date())) {
                return null;
            }

            final String userId = claims.get(USER_ID_CLAIM, String.class);
            final Object rolesObj = claims.get(ROLES_CLAIM);
            final String roles = rolesObj != null ? String.join(",", (java.util.List<String>) rolesObj) : "";
            return new Identity(userId, roles);
        } catch (Exception e) {
            log.debug("Rejected a malformed access token on {}: {}", path, e.getMessage());
            return null;
        }
    }

    /** Answers the request with a status instead of letting it through. */
    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    /** Drops the identity headers a client could otherwise claim for itself. */
    private ServerWebExchange stripIdentityHeaders(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> {
                            headers.remove(USER_ID_HEADER);
                            headers.remove(USER_ROLES_HEADER);
                        })
                        .build())
                .build();
    }

    /**
     * Asks the blacklist whether the token was revoked.
     *
     * <p>Fails open: an unreachable Redis must not stop every authenticated request, and the
     * exposure is limited by the access token lifetime.
     *
     * @param token the access token to look up
     * @return whether the token is revoked
     */
    private Mono<Boolean> isRevoked(String token) {
        if (redisTemplate == null) {
            return Mono.just(false);
        }
        return redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token)
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.warn("Cannot reach the token blacklist, accepting the token: {}",
                            e.getMessage());
                    return Mono.just(false);
                });
    }

    private boolean isPublicPath(String path, String method) {
        if (path.startsWith("/fallback")) {
            return true;
        }
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register") ||
                path.startsWith("/api/auth/refresh")) {
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
        return path.startsWith("/api/inventory/reserve") ||
                path.startsWith("/api/inventory/release");
    }

    private Key getSignInKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    @Override
    public int getOrder() {
        return AUTH_FILTER_ORDER;
    }

    /** What the token says about the caller. */
    private record Identity(String userId, String roles) {
    }
}
