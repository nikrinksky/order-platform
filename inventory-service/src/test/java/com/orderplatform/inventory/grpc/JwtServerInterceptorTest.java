package com.orderplatform.inventory.grpc;

import com.orderplatform.common.security.JwtService;
import com.orderplatform.grpc.inventory.InventoryServiceGrpc;
import com.orderplatform.grpc.inventory.ReserveRequest;
import com.orderplatform.grpc.inventory.ReserveResponse;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the gRPC authentication interceptor.
 *
 * <p>The interceptor is the only thing standing between the gRPC port and the stock: a gRPC call
 * never passes through the servlet filter chain, so every case below is a hole if it regresses.
 */
class JwtServerInterceptorTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha";

    private JwtService jwtService;
    private JwtServerInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        interceptor = new JwtServerInterceptor(jwtService);
    }

    @Test
    void shouldRejectCallWithoutToken() {
        ServerCall<ReserveRequest, ReserveResponse> call = reserveCall();
        ServerCallHandler<ReserveRequest, ReserveResponse> next = nextHandler();

        interceptor.interceptCall(call, new Metadata(), next);

        verify(call).close(argThat(status -> status.getCode() == Status.Code.UNAUTHENTICATED), any(Metadata.class));
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void shouldRejectTokenWithoutBearerPrefix() {
        ServerCall<ReserveRequest, ReserveResponse> call = reserveCall();
        ServerCallHandler<ReserveRequest, ReserveResponse> next = nextHandler();
        Metadata headers = new Metadata();
        headers.put(JwtServerInterceptor.AUTHORIZATION, accessToken("admin", "ROLE_ADMIN"));

        interceptor.interceptCall(call, headers, next);

        verify(call).close(argThat(status -> status.getCode() == Status.Code.UNAUTHENTICATED), any(Metadata.class));
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void shouldRejectInvalidToken() {
        ServerCall<ReserveRequest, ReserveResponse> call = reserveCall();
        ServerCallHandler<ReserveRequest, ReserveResponse> next = nextHandler();
        Metadata headers = bearer("not-a-jwt");

        interceptor.interceptCall(call, headers, next);

        verify(call).close(argThat(status -> status.getCode() == Status.Code.UNAUTHENTICATED), any(Metadata.class));
        verify(next, never()).startCall(any(), any());
    }

    /**
     * A refresh token is signed with the same key, so only the token type claim tells it apart.
     */
    @Test
    void shouldRejectRefreshToken() {
        ServerCall<ReserveRequest, ReserveResponse> call = reserveCall();
        ServerCallHandler<ReserveRequest, ReserveResponse> next = nextHandler();

        interceptor.interceptCall(call, bearer(token("admin", "refresh", List.of("ROLE_ADMIN"))), next);

        verify(call).close(argThat(status -> status.getCode() == Status.Code.UNAUTHENTICATED), any(Metadata.class));
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void shouldRejectExpiredToken() {
        ServerCall<ReserveRequest, ReserveResponse> call = reserveCall();
        ServerCallHandler<ReserveRequest, ReserveResponse> next = nextHandler();
        String expired = Jwts.builder()
                .setSubject("admin")
                .claim("token_use", "access")
                .claim("roles", List.of("ROLE_ADMIN"))
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();

        interceptor.interceptCall(call, bearer(expired), next);

        verify(call).close(argThat(status -> status.getCode() == Status.Code.UNAUTHENTICATED), any(Metadata.class));
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void shouldRefuseReserveForRegularUser() {
        ServerCall<ReserveRequest, ReserveResponse> call = reserveCall();
        ServerCallHandler<ReserveRequest, ReserveResponse> next = nextHandler();

        interceptor.interceptCall(call, bearer(accessToken("visitor", "ROLE_USER")), next);

        verify(call).close(argThat(status -> status.getCode() == Status.Code.PERMISSION_DENIED), any(Metadata.class));
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void shouldRefuseReserveWhenTokenCarriesNoRoles() {
        ServerCall<ReserveRequest, ReserveResponse> call = reserveCall();
        ServerCallHandler<ReserveRequest, ReserveResponse> next = nextHandler();

        interceptor.interceptCall(call, bearer(accessToken("visitor")), next);

        verify(call).close(argThat(status -> status.getCode() == Status.Code.PERMISSION_DENIED), any(Metadata.class));
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void shouldAllowReserveForAdmin() {
        ServerCall<ReserveRequest, ReserveResponse> call = reserveCall();
        ServerCallHandler<ReserveRequest, ReserveResponse> next = nextHandler();
        Metadata headers = bearer(accessToken("admin", "ROLE_ADMIN"));

        interceptor.interceptCall(call, headers, next);

        verify(next).startCall(call, headers);
        verify(call, never()).close(any(), any());
    }

    @Test
    void shouldAllowReleaseForManager() {
        ServerCall<Object, Object> call = call(InventoryServiceGrpc.getReleaseMethod());
        ServerCallHandler<Object, Object> next = nextHandler();

        interceptor.interceptCall(call, bearer(accessToken("manager", "ROLE_MANAGER")), next);

        verify(next).startCall(any(), any());
    }

    /**
     * Committing only finalises an order that already holds the stock, so any authenticated caller
     * is enough; requiring a role here would break the transition for the operator who owns it.
     */
    @Test
    void shouldAllowCommitForRegularUser() {
        ServerCall<Object, Object> call = call(InventoryServiceGrpc.getCommitMethod());
        ServerCallHandler<Object, Object> next = nextHandler();

        interceptor.interceptCall(call, bearer(accessToken("visitor", "ROLE_USER")), next);

        verify(next).startCall(any(), any());
    }

    @SuppressWarnings("unchecked")
    private static ServerCall<ReserveRequest, ReserveResponse> reserveCall() {
        return (ServerCall<ReserveRequest, ReserveResponse>) (ServerCall<?, ?>) call(
                InventoryServiceGrpc.getReserveMethod());
    }

    @SuppressWarnings("unchecked")
    private static ServerCall<Object, Object> call(MethodDescriptor<?, ?> descriptor) {
        ServerCall<Object, Object> call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn((MethodDescriptor<Object, Object>) descriptor);
        return call;
    }

    @SuppressWarnings("unchecked")
    private static <ReqT, RespT> ServerCallHandler<ReqT, RespT> nextHandler() {
        ServerCallHandler<ReqT, RespT> next = mock(ServerCallHandler.class);
        when(next.startCall(any(), any())).thenReturn(new ServerCall.Listener<>() {
        });
        return next;
    }

    private static Metadata bearer(String token) {
        Metadata headers = new Metadata();
        headers.put(JwtServerInterceptor.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    private static String accessToken(String subject, String... roles) {
        return token(subject, "access", List.of(roles));
    }

    private static String token(String subject, String type, List<String> roles) {
        return Jwts.builder()
                .setSubject(subject)
                .claim("token_use", type)
                .claim("roles", roles)
                .setExpiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
    }
}
