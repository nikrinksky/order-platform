package com.orderplatform.product.grpc;

import com.orderplatform.grpc.product.GetProductRequest;
import com.orderplatform.grpc.product.ProductCatalogGrpc;
import com.orderplatform.grpc.product.ProductReply;
import com.orderplatform.product.dto.ProductResponse;
import com.orderplatform.product.service.ProductNotFoundException;
import com.orderplatform.product.service.ProductService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.format.DateTimeFormatter;

/**
 * The gRPC face of the catalogue, used by the gateway for the product page.
 *
 * <p>It is an adapter over {@link ProductService}, so the visibility rule (an unpublished product
 * looks missing to a visitor) is not reimplemented here and cannot drift from the REST endpoint.
 *
 * <p>The caller's identity arrives in the gRPC context; the security context is populated for the
 * duration of the call because {@code CatalogAccess} reads it. It is cleared afterwards, so a call
 * served next by the same thread cannot inherit the previous caller's roles.
 */
@Slf4j
@GrpcService(interceptors = OptionalJwtServerInterceptor.class)
@RequiredArgsConstructor
public class ProductGrpcEndpoint extends ProductCatalogGrpc.ProductCatalogImplBase {

    /** Same text Jackson writes for a {@code LocalDateTime}, so the JSON does not change. */
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ProductService productService;

    @Override
    public void getProduct(GetProductRequest request, StreamObserver<ProductReply> responseObserver) {
        Authentication authentication = OptionalJwtServerInterceptor.AUTHENTICATION.get();
        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        try {
            ProductResponse product = productService.findById(request.getId());
            responseObserver.onNext(toReply(product));
            responseObserver.onCompleted();
        } catch (ProductNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (RuntimeException e) {
            log.error("gRPC product lookup failed for id {}", request.getId(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Product lookup failed")
                    .asRuntimeException());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static ProductReply toReply(ProductResponse product) {
        ProductReply.Builder reply = ProductReply.newBuilder()
                .setId(nullToEmpty(product.getId()))
                .setName(nullToEmpty(product.getName()))
                .setDescription(nullToEmpty(product.getDescription()))
                .setPrice(product.getPrice() == null ? "" : product.getPrice().toPlainString())
                .setCategory(nullToEmpty(product.getCategory()))
                .setQuantity(product.getQuantity() == null ? 0 : product.getQuantity())
                .setActive(product.isActive());
        if (product.getCreatedAt() != null) {
            reply.setCreatedAt(product.getCreatedAt().format(TIMESTAMP));
        }
        if (product.getUpdatedAt() != null) {
            reply.setUpdatedAt(product.getUpdatedAt().format(TIMESTAMP));
        }
        return reply.build();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
