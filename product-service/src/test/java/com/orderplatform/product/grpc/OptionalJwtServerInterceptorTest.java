package com.orderplatform.product.grpc;

import com.orderplatform.common.security.JwtService;
import com.orderplatform.grpc.product.ProductCatalogGrpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the optional-token interceptor of the catalogue.
 *
 * <p>It never rejects - the catalogue is public - so what matters is that a valid token becomes an
 * identity, and that everything else degrades to an anonymous caller instead of failing the call.
 */
class OptionalJwtServerInterceptorTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha";

    private OptionalJwtServerInterceptor interceptor;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        interceptor = new OptionalJwtServerInterceptor(jwtService);
    }

    @Test
    void shouldPassAnAnonymousCallWithoutIdentity() {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        ServerCallHandler<Object, Object> next = nextCapturing(seen);

        interceptor.interceptCall(call(), new Metadata(), next);

        assertNull(seen.get());
    }

    @Test
    void shouldBuildTheIdentityFromAValidToken() {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        ServerCallHandler<Object, Object> next = nextCapturing(seen);

        interceptor.interceptCall(call(), bearer(token("admin", List.of("ROLE_ADMIN"))), next);

        Authentication authentication = seen.get();
        assertNotNull(authentication);
        assertEquals("admin", authentication.getName());
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertEquals(List.of("ROLE_ADMIN"), roles);
        assertTrue(authentication.isAuthenticated());
    }

    /** A malformed token is not an error on a public endpoint: the caller is just anonymous. */
    @Test
    void shouldTreatAMalformedTokenAsAnonymous() {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        ServerCallHandler<Object, Object> next = nextCapturing(seen);

        interceptor.interceptCall(call(), bearer("not-a-jwt"), next);

        assertNull(seen.get());
    }

    @Test
    void shouldTreatARefreshTokenAsAnonymous() {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        ServerCallHandler<Object, Object> next = nextCapturing(seen);

        interceptor.interceptCall(call(), bearer(token("admin", List.of("ROLE_ADMIN"), "refresh")), next);

        assertNull(seen.get());
    }

    @Test
    void shouldTreatAnExpiredTokenAsAnonymous() {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        ServerCallHandler<Object, Object> next = nextCapturing(seen);
        String expired = Jwts.builder()
                .setSubject("admin")
                .claim("token_use", "access")
                .claim("roles", List.of("ROLE_ADMIN"))
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();

        interceptor.interceptCall(call(), bearer(expired), next);

        assertNull(seen.get());
    }

    @Test
    void shouldIgnoreAHeaderWithoutTheBearerPrefix() {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        ServerCallHandler<Object, Object> next = nextCapturing(seen);
        Metadata headers = new Metadata();
        headers.put(OptionalJwtServerInterceptor.AUTHORIZATION, token("admin", List.of("ROLE_ADMIN")));

        interceptor.interceptCall(call(), headers, next);

        assertNull(seen.get());
    }

    @Test
    void shouldBuildTheIdentityWithoutRoles() {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        ServerCallHandler<Object, Object> next = nextCapturing(seen);

        interceptor.interceptCall(call(), bearer(token("visitor", List.of())), next);

        assertNotNull(seen.get());
        assertTrue(seen.get().getAuthorities().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static ServerCall<Object, Object> call() {
        ServerCall<Object, Object> call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn(
                (MethodDescriptor<Object, Object>) (MethodDescriptor<?, ?>) ProductCatalogGrpc.getGetProductMethod());
        return call;
    }

    @SuppressWarnings("unchecked")
    private static ServerCallHandler<Object, Object> nextCapturing(AtomicReference<Authentication> seen) {
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        when(next.startCall(any(), any())).thenAnswer(invocation -> {
            seen.set(OptionalJwtServerInterceptor.AUTHENTICATION.get());
            return new ServerCall.Listener<>() {
            };
        });
        return next;
    }

    private static Metadata bearer(String token) {
        Metadata headers = new Metadata();
        headers.put(OptionalJwtServerInterceptor.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    private static String token(String subject, List<String> roles) {
        return token(subject, roles, "access");
    }

    private static String token(String subject, List<String> roles, String type) {
        return Jwts.builder()
                .setSubject(subject)
                .claim("token_use", type)
                .claim("roles", roles)
                .setExpiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
    }

    /** The interceptor must never close the call; the assertion keeps the mock honest. */
    @Test
    void shouldNeverRejectACall() {
        ServerCall<Object, Object> call = call();

        interceptor.interceptCall(call, new Metadata(), nextCapturing(new AtomicReference<>()));

        verify(call, org.mockito.Mockito.never()).close(any(), any());
    }
}
