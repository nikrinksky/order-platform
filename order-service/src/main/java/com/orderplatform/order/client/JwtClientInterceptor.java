package com.orderplatform.order.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Relays the caller's bearer token to inventory-service.
 *
 * <p>gRPC calls bypass the servlet chain, so the token does not travel on its own. The interceptor
 * copies the {@code Authorization} header of the request being served into the call metadata,
 * which is what {@code JwtServerInterceptor} on the other side validates. A call made outside a
 * request (no header to relay) is sent without credentials and is refused downstream, which keeps
 * the reservation fail-closed instead of reserving stock without an identity.
 */
@Component
public class JwtClientInterceptor implements ClientInterceptor {

    /** Metadata key the token travels in. Lower case is mandated by the gRPC specification. */
    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String authorization = currentAuthorization();
                if (authorization != null) {
                    headers.put(AUTHORIZATION, authorization);
                }
                super.start(responseListener, headers);
            }
        };
    }

    private static String currentAuthorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }
}
