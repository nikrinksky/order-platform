package com.orderplatform.product.grpc;

import com.orderplatform.common.security.JwtService;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads the caller's identity out of the gRPC metadata when a token is present.
 *
 * <p>Unlike the inventory interceptor this one never rejects: the catalogue is public, and an
 * anonymous visitor is a valid caller. The token only decides whether the caller is allowed to see
 * products that are not published yet, which is the same rule the REST endpoint applies.
 *
 * <p>The identity is put into the gRPC {@link Context} rather than into the thread-local security
 * context: the interceptor runs before the service method and the thread-local would leak into the
 * next call served by the same thread. The endpoint moves it across for the duration of the call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OptionalJwtServerInterceptor implements ServerInterceptor {

    /** Metadata key the token travels in. Lower case is mandated by the gRPC specification. */
    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    /** Identity of the current call, absent for an anonymous visitor. */
    static final Context.Key<Authentication> AUTHENTICATION = Context.key("authentication");

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        Authentication authentication = authenticate(headers.get(AUTHORIZATION));
        if (authentication == null) {
            return next.startCall(call, headers);
        }
        return Contexts.interceptCall(Context.current().withValue(AUTHENTICATION, authentication),
                call, headers, next);
    }

    /**
     * Builds the authentication the token describes.
     *
     * @param header raw {@code authorization} value, possibly null
     * @return the authentication, or null when there is no usable token - which is not an error
     */
    private Authentication authenticate(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (!jwtService.isValidAccessToken(token)) {
            // A malformed or expired token is treated as no token: the catalogue stays readable,
            // the caller just does not get the privileged view.
            log.debug("Ignoring an invalid token on a catalogue call");
            return null;
        }
        List<GrantedAuthority> authorities = jwtService.extractRoles(token).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        return new UsernamePasswordAuthenticationToken(jwtService.extractUsername(token), null, authorities);
    }
}
