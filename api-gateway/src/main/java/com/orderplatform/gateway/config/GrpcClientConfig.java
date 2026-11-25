package com.orderplatform.gateway.config;

import com.orderplatform.grpc.product.ProductCatalogGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

/**
 * Wires the product stub to its named channel.
 *
 * <p>The stub lives in {@code common-grpc}, outside the gateway package, so the automatic scan for
 * gRPC stubs does not find it and the bean is declared here. The channel is described in
 * {@code spring.grpc.client.channels.product}: the address and the deadline are configuration.
 */
@Configuration
public class GrpcClientConfig {

    /** Name of the channel in {@code spring.grpc.client.channels}. */
    static final String CHANNEL_NAME = "product";

    @Bean
    public ProductCatalogGrpc.ProductCatalogBlockingStub productCatalogBlockingStub(
            GrpcChannelFactory channels) {
        return ProductCatalogGrpc.newBlockingStub(channels.createChannel(CHANNEL_NAME));
    }
}
