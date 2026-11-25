package com.orderplatform.product.grpc;

import com.orderplatform.grpc.product.GetProductRequest;
import com.orderplatform.grpc.product.ProductReply;
import com.orderplatform.product.dto.ProductResponse;
import com.orderplatform.product.service.ProductNotFoundException;
import com.orderplatform.product.service.ProductService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the gRPC catalogue adapter.
 *
 * <p>The adapter must keep two promises: the reply carries the same values the REST endpoint
 * serialises, and a missing product is {@code NOT_FOUND} rather than an internal error.
 */
@ExtendWith(MockitoExtension.class)
class ProductGrpcEndpointTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductGrpcEndpoint endpoint;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnTheProductWithEveryField() {
        when(productService.findById("prod-1")).thenReturn(ProductResponse.builder()
                .id("prod-1")
                .name("Phone")
                .description("A phone")
                .price(new BigDecimal("999.90"))
                .category("Electronics")
                .quantity(7)
                .active(true)
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5))
                .updatedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 6))
                .build());
        CapturingObserver observer = new CapturingObserver();

        endpoint.getProduct(GetProductRequest.newBuilder().setId("prod-1").build(), observer);

        assertTrue(observer.completed);
        ProductReply reply = observer.values.get(0);
        assertEquals("prod-1", reply.getId());
        assertEquals("Phone", reply.getName());
        assertEquals("A phone", reply.getDescription());
        assertEquals("999.90", reply.getPrice());
        assertEquals("Electronics", reply.getCategory());
        assertEquals(7, reply.getQuantity());
        assertTrue(reply.getActive());
        assertEquals("2026-01-02T03:04:05", reply.getCreatedAt());
        assertEquals("2026-01-02T03:04:06", reply.getUpdatedAt());
    }

    /**
     * A price is a decimal, and a {@code double} would turn 999.90 into 999.9000000000001. The
     * reply carries the exact text instead.
     */
    @Test
    void shouldKeepThePriceExact() {
        when(productService.findById("prod-1")).thenReturn(ProductResponse.builder()
                .id("prod-1")
                .price(new BigDecimal("0.10"))
                .build());
        CapturingObserver observer = new CapturingObserver();

        endpoint.getProduct(GetProductRequest.newBuilder().setId("prod-1").build(), observer);

        assertEquals("0.10", observer.values.get(0).getPrice());
    }

    @Test
    void shouldReportMissingProductAsNotFound() {
        when(productService.findById("missing"))
                .thenThrow(new ProductNotFoundException("Product not found with id: missing"));
        CapturingObserver observer = new CapturingObserver();

        endpoint.getProduct(GetProductRequest.newBuilder().setId("missing").build(), observer);

        StatusRuntimeException error = assertInstanceOf(StatusRuntimeException.class, observer.error);
        assertEquals(Status.Code.NOT_FOUND, error.getStatus().getCode());
        assertEquals("Product not found with id: missing", error.getStatus().getDescription());
    }

    /**
     * A database failure must not be reported as "no such product": the client would cache a
     * negative answer for a product that exists.
     */
    @Test
    void shouldReportUnexpectedFailureAsInternal() {
        when(productService.findById("prod-1")).thenThrow(new IllegalStateException("mongo is down"));
        CapturingObserver observer = new CapturingObserver();

        endpoint.getProduct(GetProductRequest.newBuilder().setId("prod-1").build(), observer);

        StatusRuntimeException error = assertInstanceOf(StatusRuntimeException.class, observer.error);
        assertEquals(Status.Code.INTERNAL, error.getStatus().getCode());
        assertEquals("Product lookup failed", error.getStatus().getDescription());
    }

    /**
     * {@code CatalogAccess} reads the security context, so the endpoint has to publish the caller's
     * identity while the service runs - otherwise an administrator would see a draft product as
     * missing.
     */
    @Test
    void shouldExposeTheCallerIdentityToTheService() {
        Authentication admin = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        AtomicReference<Authentication> seen = new AtomicReference<>();
        when(productService.findById("draft")).thenAnswer(invocation -> {
            seen.set(SecurityContextHolder.getContext().getAuthentication());
            return ProductResponse.builder().id("draft").build();
        });
        io.grpc.Context context = io.grpc.Context.current()
                .withValue(OptionalJwtServerInterceptor.AUTHENTICATION, admin);
        CapturingObserver observer = new CapturingObserver();

        context.run(() -> endpoint.getProduct(
                GetProductRequest.newBuilder().setId("draft").build(), observer));

        assertEquals(admin, seen.get());
        assertTrue(observer.completed);
    }

    /** The context must not survive the call: the thread is reused for the next request. */
    @Test
    void shouldClearTheSecurityContextAfterTheCall() {
        when(productService.findById("prod-1")).thenReturn(ProductResponse.builder().id("prod-1").build());
        Authentication admin = new UsernamePasswordAuthenticationToken("admin", null, List.of());

        io.grpc.Context.current()
                .withValue(OptionalJwtServerInterceptor.AUTHENTICATION, admin)
                .run(() -> endpoint.getProduct(
                        GetProductRequest.newBuilder().setId("prod-1").build(), new CapturingObserver()));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldAnswerAnAnonymousCall() {
        when(productService.findById("prod-1")).thenReturn(ProductResponse.builder()
                .id("prod-1").name("Phone").active(true).build());
        CapturingObserver observer = new CapturingObserver();

        endpoint.getProduct(GetProductRequest.newBuilder().setId("prod-1").build(), observer);

        assertTrue(observer.completed);
        assertEquals("Phone", observer.values.get(0).getName());
        assertNull(observer.error);
    }

    @Test
    void shouldMapNullFieldsToEmptyValues() {
        when(productService.findById("prod-1")).thenReturn(ProductResponse.builder().id("prod-1").build());
        CapturingObserver observer = new CapturingObserver();

        endpoint.getProduct(GetProductRequest.newBuilder().setId("prod-1").build(), observer);

        ProductReply reply = observer.values.get(0);
        assertEquals("", reply.getName());
        assertEquals("", reply.getPrice());
        assertEquals(0, reply.getQuantity());
        assertFalse(reply.getActive());
    }

    /** Minimal observer that records what the endpoint answered. */
    private static final class CapturingObserver implements StreamObserver<ProductReply> {

        private final List<ProductReply> values = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(ProductReply value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
