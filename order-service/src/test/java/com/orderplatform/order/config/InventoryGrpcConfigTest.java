package com.orderplatform.order.config;

import com.orderplatform.grpc.inventory.InventoryServiceGrpc;
import com.orderplatform.order.client.JwtClientInterceptor;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the stub wiring.
 *
 * <p>The channel name is what ties the stub to the address in {@code application.yml}: a typo here
 * silently falls back to the default channel, and the calls go to the wrong place.
 */
class InventoryGrpcConfigTest {

    @Test
    void shouldBindStubToTheNamedChannel() {
        GrpcChannelFactory channels = mock(GrpcChannelFactory.class);
        JwtClientInterceptor interceptor = mock(JwtClientInterceptor.class);
        when(channels.createChannel(eq(InventoryGrpcConfig.CHANNEL_NAME), any(ChannelBuilderOptions.class)))
                .thenReturn(mock(ManagedChannel.class));

        InventoryServiceGrpc.InventoryServiceBlockingStub stub =
                new InventoryGrpcConfig().inventoryBlockingStub(channels, interceptor);

        assertNotNull(stub);
        verify(channels).createChannel(eq("inventory"), any(ChannelBuilderOptions.class));
    }

    @Test
    void shouldAttachOnlyTheTokenInterceptorToTheChannel() {
        GrpcChannelFactory channels = mock(GrpcChannelFactory.class);
        JwtClientInterceptor interceptor = mock(JwtClientInterceptor.class);
        when(channels.createChannel(eq(InventoryGrpcConfig.CHANNEL_NAME), any(ChannelBuilderOptions.class)))
                .thenReturn(mock(ManagedChannel.class));
        ArgumentCaptor<ChannelBuilderOptions> options = ArgumentCaptor.forClass(ChannelBuilderOptions.class);

        new InventoryGrpcConfig().inventoryBlockingStub(channels, interceptor);

        verify(channels).createChannel(eq(InventoryGrpcConfig.CHANNEL_NAME), options.capture());
        List<ClientInterceptor> interceptors = options.getValue().interceptors();
        assertEquals(1, interceptors.size());
        assertSame(interceptor, interceptors.get(0));
    }
}
