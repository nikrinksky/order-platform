package com.orderplatform.order.config;

import com.orderplatform.grpc.inventory.InventoryServiceGrpc;
import com.orderplatform.order.client.JwtClientInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.util.List;

/**
 * Wires the inventory stub to its named channel.
 *
 * <p>The channel is described in {@code spring.grpc.client.channels.inventory}: the address and the
 * deadline live in configuration, not in code. The JWT interceptor is attached per channel so it
 * cannot leak onto a future channel that talks to a service with a different authentication model.
 */
@Configuration
public class InventoryGrpcConfig {

    /** Name of the channel in {@code spring.grpc.client.channels}. */
    static final String CHANNEL_NAME = "inventory";

    @Bean
    public InventoryServiceGrpc.InventoryServiceBlockingStub inventoryBlockingStub(
            GrpcChannelFactory channels, JwtClientInterceptor jwtClientInterceptor) {
        return InventoryServiceGrpc.newBlockingStub(channels.createChannel(CHANNEL_NAME,
                ChannelBuilderOptions.defaults().withInterceptors(List.of(jwtClientInterceptor))));
    }
}
