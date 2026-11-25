package com.orderplatform.gateway.client;

import com.orderplatform.grpc.product.GetProductRequest;
import com.orderplatform.grpc.product.ProductCatalogGrpc;
import com.orderplatform.grpc.product.ProductReply;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;

/**
 * Reads the catalogue over gRPC.
 *
 * <p>The gRPC stub is blocking, and the gateway runs on a Netty event loop: calling it directly
 * would park the thread that serves every other request on this connection. The call is therefore
 * moved to the bounded-elastic pool, which is what that pool exists for.
 */
@Component
public class ProductCatalogClient {

    /** Metadata key the token travels in. Lower case is mandated by the gRPC specification. */
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ProductCatalogGrpc.ProductCatalogBlockingStub productStub;

    public ProductCatalogClient(ProductCatalogGrpc.ProductCatalogBlockingStub productStub) {
        this.productStub = productStub;
    }

    /**
     * Fetches one product.
     *
     * @param id            product id
     * @param authorization the caller's {@code Authorization} header, or null for an anonymous
     *                      visitor; it decides whether an unpublished product is visible
     * @return the product, or an error the caller maps to a status
     */
    public Mono<ProductView> findById(String id, String authorization) {
        return Mono.fromCallable(() -> toView(stubWith(authorization).getProduct(
                        GetProductRequest.newBuilder().setId(id).build())))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ProductCatalogGrpc.ProductCatalogBlockingStub stubWith(String authorization) {
        if (authorization == null) {
            return productStub;
        }
        Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, authorization);
        return productStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static ProductView toView(ProductReply reply) {
        return new ProductView(
                reply.getId(),
                reply.getName(),
                reply.getDescription(),
                reply.getPrice().isEmpty() ? null : new BigDecimal(reply.getPrice()),
                reply.getCategory(),
                reply.getQuantity(),
                reply.getActive(),
                reply.getCreatedAt(),
                reply.getUpdatedAt());
    }

    /**
     * The product as the REST endpoint returns it.
     *
     * <p>The field names are the ones {@code ProductResponse} serialises to, so a client cannot
     * tell which transport answered.
     */
    public record ProductView(
            String id,
            String name,
            String description,
            BigDecimal price,
            String category,
            Integer quantity,
            boolean active,
            String createdAt,
            String updatedAt) {
    }
}
