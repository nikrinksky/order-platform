package com.orderplatform.order.client;

import com.orderplatform.grpc.inventory.InventoryServiceGrpc;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the client interceptor that relays the caller's token.
 *
 * <p>inventory-service refuses an unauthenticated gRPC call, so the token has to reach the
 * metadata; and it must not be invented when there is no request to take it from.
 */
class JwtClientInterceptorTest {

    private final JwtClientInterceptor interceptor = new JwtClientInterceptor();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldRelayAuthorizationHeader() {
        givenRequestWithAuthorization("Bearer token-value");
        Metadata headers = callAndCaptureHeaders();

        assertEquals("Bearer token-value", headers.get(JwtClientInterceptor.AUTHORIZATION));
    }

    /**
     * A call made outside a request has no token to relay. Sending a stale or empty one would be
     * worse than sending nothing: the server answers with a clear refusal.
     */
    @Test
    void shouldSendNoTokenWhenThereIsNoRequest() {
        Metadata headers = callAndCaptureHeaders();

        assertNull(headers.get(JwtClientInterceptor.AUTHORIZATION));
    }

    @Test
    void shouldSendNoTokenWhenTheRequestHasNoAuthorizationHeader() {
        givenRequestWithAuthorization(null);
        Metadata headers = callAndCaptureHeaders();

        assertNull(headers.get(JwtClientInterceptor.AUTHORIZATION));
    }

    @SuppressWarnings("unchecked")
    private Metadata callAndCaptureHeaders() {
        Channel channel = mock(Channel.class);
        ClientCall<Object, Object> call = mock(ClientCall.class);
        when(channel.newCall(any(), any())).thenReturn(call);
        MethodDescriptor<Object, Object> method =
                (MethodDescriptor<Object, Object>) (MethodDescriptor<?, ?>) InventoryServiceGrpc.getReserveMethod();

        ClientCall<Object, Object> intercepted =
                interceptor.interceptCall(method, CallOptions.DEFAULT, channel);
        Metadata headers = new Metadata();
        intercepted.start(mock(ClientCall.Listener.class), headers);

        verify(call).start(any(), any());
        return headers;
    }

    private static void givenRequestWithAuthorization(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
