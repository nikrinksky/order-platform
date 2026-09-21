package com.orderplatform.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtAuthGlobalFilterTest {

    private static final String SECRET =
            "unitTestSecretKeyForJwtAuthGlobalFilterAtLeast256BitsLong!!";

    private JwtAuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthGlobalFilter();
        ReflectionTestUtils.setField(filter, "secret", SECRET);
    }

    private Key signingKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    private String token(String tokenUse, Date expiration, Object roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("token_use", tokenUse);
        claims.put("userId", "user-42");
        if (roles != null) {
            claims.put("roles", roles);
        }
        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(expiration)
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Runs the filter against a mocked exchange and reports the outcome:
     * 200 when the filter chain was invoked, otherwise the response status set by the filter.
     */
    private HttpStatus runFilter(HttpMethod method, String path,
            String authorizationHeader) {
        MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.method(method, path);
        if (authorizationHeader != null) {
            builder.header("Authorization", authorizationHeader);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        boolean[] chained = {false};
        GatewayFilterChain chain = e -> {
            chained[0] = true;
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        if (chained[0]) {
            return HttpStatus.OK;
        }
        return HttpStatus.valueOf(exchange.getResponse().getStatusCode().value());
    }

    private HttpStatus runFilter(HttpMethod method, String path) {
        return runFilter(method, path, null);
    }

    @Test
    void shouldBlockInternalReserveEndpoint() {
        assertEquals(HttpStatus.FORBIDDEN,
                runFilter(HttpMethod.GET,
                        "/api/inventory/reserve", "Bearer whatever"));
    }

    @Test
    void shouldBlockInternalReleaseEndpoint() {
        assertEquals(HttpStatus.FORBIDDEN,
                runFilter(HttpMethod.GET,
                        "/api/inventory/release", "Bearer whatever"));
    }

    @Test
    void shouldAllowFallbackWithoutToken() {
        assertEquals(HttpStatus.OK,
                runFilter(HttpMethod.GET, "/fallback/auth"));
    }

    @Test
    void shouldAllowLoginWithoutToken() {
        assertEquals(HttpStatus.OK,
                runFilter(HttpMethod.POST, "/api/auth/login"));
    }

    @Test
    void shouldAllowRegisterWithoutToken() {
        assertEquals(HttpStatus.OK,
                runFilter(HttpMethod.POST, "/api/auth/register"));
    }

    @Test
    void shouldAllowRefreshWithoutToken() {
        assertEquals(HttpStatus.OK,
                runFilter(HttpMethod.POST, "/api/auth/refresh"));
    }

    @Test
    void shouldAllowActuatorWithoutToken() {
        assertEquals(HttpStatus.OK,
                runFilter(HttpMethod.GET, "/actuator/health"));
    }

    @Test
    void shouldAllowPublicProductGet() {
        assertEquals(HttpStatus.OK,
                runFilter(HttpMethod.GET, "/api/products/1"));
    }

    @Test
    void shouldRejectPublicProductPost() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                runFilter(HttpMethod.POST, "/api/products"));
    }

    @Test
    void shouldRequireTokenForProtectedPath() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                runFilter(HttpMethod.GET, "/api/orders"));
    }

    @Test
    void shouldRejectNonBearerHeader() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                runFilter(HttpMethod.GET, "/api/orders",
                        "Basic dXNlcjpwYXNz"));
    }

    @Test
    void shouldPassValidAccessTokenAndForwardIdentity() {
        String token = token("access", new Date(System.currentTimeMillis() + 3_600_000),
                List.of("USER", "ADMIN"));
        MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.method(
                HttpMethod.GET, "/api/orders")
                .header("Authorization", "Bearer " + token);
        MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());

        Map<String, String> forwarded = new HashMap<>();
        GatewayFilterChain chain = e -> {
            forwarded.put("userId", e.getRequest().getHeaders().getFirst("X-User-Id"));
            forwarded.put("roles", e.getRequest().getHeaders().getFirst("X-User-Roles"));
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();

        assertEquals("user-42", forwarded.get("userId"));
        assertEquals("USER,ADMIN", forwarded.get("roles"));
    }

    @Test
    void shouldForwardEmptyRolesWhenRolesClaimMissing() {
        String token = token("access", new Date(System.currentTimeMillis() + 3_600_000), null);
        MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.method(
                HttpMethod.GET, "/api/orders")
                .header("Authorization", "Bearer " + token);
        MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());

        Map<String, String> forwarded = new HashMap<>();
        GatewayFilterChain chain = e -> {
            forwarded.put("roles", e.getRequest().getHeaders().getFirst("X-User-Roles"));
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();

        assertEquals("", forwarded.get("roles"));
    }

    @Test
    void shouldRejectExpiredAccessToken() {
        String token = token("access", new Date(System.currentTimeMillis() - 60_000), null);

        assertEquals(HttpStatus.UNAUTHORIZED,
                runFilter(HttpMethod.GET, "/api/orders",
                        "Bearer " + token));
    }

    @Test
    void shouldRejectRefreshTokenOnProtectedPath() {
        String token = token("refresh", new Date(System.currentTimeMillis() + 3_600_000), null);

        assertEquals(HttpStatus.UNAUTHORIZED,
                runFilter(HttpMethod.GET, "/api/orders",
                        "Bearer " + token));
    }

    @Test
    void shouldRejectTamperedSignature() {
        Key otherKey = Keys.hmacShaKeyFor((SECRET + "tampered").getBytes());
        String token = Jwts.builder()
                .setClaims(Map.of("token_use", "access", "userId", "user-42"))
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(otherKey, SignatureAlgorithm.HS256)
                .compact();

        assertEquals(HttpStatus.UNAUTHORIZED,
                runFilter(HttpMethod.GET, "/api/orders",
                        "Bearer " + token));
    }

    @Test
    void shouldHaveHighPrecedenceOrder() {
        assertEquals(-100, filter.getOrder());
    }
}

