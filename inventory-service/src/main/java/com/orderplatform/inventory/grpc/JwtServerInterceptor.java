package com.orderplatform.inventory.grpc;

import com.orderplatform.common.security.JwtService;
import com.orderplatform.grpc.inventory.InventoryServiceGrpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Authenticates every gRPC call with the bearer token of the operator who triggered it.
 *
 * <p>The HTTP endpoints of this service are protected by the JWT filter, but a gRPC call never
 * passes through the servlet chain: without this interceptor the stock operations would be open to
 * anything that can reach the port, which is a wider hole than the anonymous reserve endpoint that
 * was closed earlier. order-service relays the caller's token in the {@code authorization}
 * metadata, exactly as it does for REST.
 *
 * <p>Roles are checked per method: reserving and releasing stock stays an administrator or manager
 * operation, while a commit is allowed for any authenticated caller because it only finalises an
 * order that already holds the stock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtServerInterceptor implements ServerInterceptor {

    /** Metadata key the token travels in. Lower case is mandated by the gRPC specification. */
    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final String BEARER_PREFIX = "Bearer ";

    /** Roles that may take stock out of circulation. */
    private static final Set<String> PRIVILEGED_ROLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER");

    /**
     * Methods that change the amount of stock held. Taken from the generated descriptors rather
     * than typed as strings, so a renamed method cannot silently lose its role check.
     */
    private static final Set<String> PRIVILEGED_METHODS = Set.of(
            InventoryServiceGrpc.getReserveMethod().getFullMethodName(),
            InventoryServiceGrpc.getReleaseMethod().getFullMethodName());

    private final JwtService jwtService;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String header = headers.get(AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return reject(call, Status.UNAUTHENTICATED.withDescription("Missing bearer token"));
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (!jwtService.isValidAccessToken(token)) {
            return reject(call, Status.UNAUTHENTICATED.withDescription("Invalid or expired token"));
        }

        String method = call.getMethodDescriptor().getFullMethodName();
        if (PRIVILEGED_METHODS.contains(method) && !hasPrivilegedRole(token)) {
            return reject(call, Status.PERMISSION_DENIED
                    .withDescription("Reserving and releasing stock requires ROLE_ADMIN or ROLE_MANAGER"));
        }

        return next.startCall(call, headers);
    }

    private boolean hasPrivilegedRole(String token) {
        List<String> roles = jwtService.extractRoles(token);
        return roles.stream().anyMatch(PRIVILEGED_ROLES::contains);
    }

    /**
     * Ends the call without invoking the service.
     *
     * <p>Returns a no-op listener because the contract requires one even for a call that is already
     * closed; returning null makes the transport fail with an internal error instead of the status
     * that explains the refusal.
     */
    private static <ReqT, RespT> ServerCall.Listener<ReqT> reject(ServerCall<ReqT, RespT> call,
                                                                  Status status) {
        log.warn("Rejected gRPC call {}: {}", call.getMethodDescriptor().getFullMethodName(),
                status.getDescription());
        call.close(status, new Metadata());
        return new ServerCall.Listener<>() {
        };
    }
}
