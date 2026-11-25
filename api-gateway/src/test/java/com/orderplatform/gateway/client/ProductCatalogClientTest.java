package com.orderplatform.gateway.client;

import com.orderplatform.grpc.product.GetProductRequest;
import com.orderplatform.grpc.product.ProductCatalogGrpc;
import com.orderplatform.grpc.product.ProductReply;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ProductCatalogClient} against a real gRPC server in the same JVM.
 *
 * <p>The client is what decides whether the product page renders, so the tests drive the generated
 * contract end to end: the reply has to be mapped to the REST shape, and the caller's token has to
 * reach the metadata, otherwise an administrator would not see a draft product.
 */
class ProductCatalogClientTest {

    private final ScriptedCatalog catalog = new ScriptedCatalog();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private Server server;
    private ManagedChannel channel;
    private ProductCatalogClient client;

    @BeforeEach
    void startServer() throws IOException {
        String name = "product-" + UUID.randomUUID();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(ServerInterceptors.intercept(catalog, captureAuthorization()))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        client = new ProductCatalogClient(ProductCatalogGrpc.newBlockingStub(channel));
    }

    /** Records the {@code authorization} metadata of the call, as the real server would read it. */
    private ServerInterceptor captureAuthorization() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                        Metadata headers,
                                                                        ServerCallHandler<ReqT, RespT> next) {
                lastAuthorization.set(headers.get(Metadata.Key.of("authorization",
                        Metadata.ASCII_STRING_MARSHALLER)));
                return next.startCall(call, headers);
            }
        };
    }

    @AfterEach
    void stopServer() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void shouldMapTheReplyToTheRestShape() {
        catalog.answer = observer -> {
            observer.onNext(ProductReply.newBuilder()
                    .setId("prod-1")
                    .setName("Phone")
                    .setDescription("A phone")
                    .setPrice("999.90")
                    .setCategory("Electronics")
                    .setQuantity(7)
                    .setActive(true)
                    .setCreatedAt("2026-01-02T03:04:05")
                    .setUpdatedAt("2026-01-02T03:04:06")
                    .build());
            observer.onCompleted();
        };

        StepVerifier.create(client.findById("prod-1", null))
                .assertNext(product -> {
                    assertEquals("prod-1", product.id());
                    assertEquals("Phone", product.name());
                    assertEquals("A phone", product.description());
                    assertEquals(new BigDecimal("999.90"), product.price());
                    assertEquals("Electronics", product.category());
                    assertEquals(7, product.quantity());
                    assertTrue(product.active());
                    assertEquals("2026-01-02T03:04:05", product.createdAt());
                    assertEquals("2026-01-02T03:04:06", product.updatedAt());
                })
                .verifyComplete();
    }

    @Test
    void shouldSendTheRequestedId() {
        catalog.answer = observer -> {
            observer.onNext(ProductReply.newBuilder().setId("prod-1").build());
            observer.onCompleted();
        };

        StepVerifier.create(client.findById("prod-1", null)).expectNextCount(1).verifyComplete();

        assertEquals("prod-1", catalog.lastRequest.get().getId());
    }

    @Test
    void shouldLeaveThePriceNullWhenTheReplyHasNone() {
        catalog.answer = observer -> {
            observer.onNext(ProductReply.newBuilder().setId("prod-1").build());
            observer.onCompleted();
        };

        StepVerifier.create(client.findById("prod-1", null))
                .assertNext(product -> assertNull(product.price()))
                .verifyComplete();
    }

    @Test
    void shouldRelayTheAuthorizationHeader() {
        catalog.answer = observer -> {
            observer.onNext(ProductReply.newBuilder().setId("prod-1").build());
            observer.onCompleted();
        };

        StepVerifier.create(client.findById("prod-1", "Bearer token-value")).expectNextCount(1).verifyComplete();

        assertEquals("Bearer token-value", lastAuthorization.get());
    }

    @Test
    void shouldSendNoTokenForAnAnonymousCall() {
        catalog.answer = observer -> {
            observer.onNext(ProductReply.newBuilder().setId("prod-1").build());
            observer.onCompleted();
        };

        StepVerifier.create(client.findById("prod-1", null)).expectNextCount(1).verifyComplete();

        assertNull(lastAuthorization.get());
    }

    /**
     * The caller maps the status to an HTTP code, so the client must not swallow it: a NOT_FOUND
     * that turned into an empty result would be served as a successful page.
     */
    @Test
    void shouldPropagateTheNotFoundStatus() {
        catalog.answer = observer -> observer.onError(
                Status.NOT_FOUND.withDescription("Product not found with id: prod-1").asRuntimeException());

        StepVerifier.create(client.findById("prod-1", null))
                .expectErrorSatisfies(error -> {
                    io.grpc.StatusRuntimeException statusError =
                            (io.grpc.StatusRuntimeException) error;
                    assertEquals(Status.Code.NOT_FOUND, statusError.getStatus().getCode());
                })
                .verify();
    }

    @Test
    void shouldPropagateAnUnavailableServer() {
        channel.shutdownNow();

        StepVerifier.create(client.findById("prod-1", null))
                .expectErrorSatisfies(error -> assertEquals(Status.Code.UNAVAILABLE,
                        ((io.grpc.StatusRuntimeException) error).getStatus().getCode()))
                .verify();
    }

    @Test
    void shouldReportAnInactiveProductAsInactive() {
        catalog.answer = observer -> {
            observer.onNext(ProductReply.newBuilder().setId("prod-1").setActive(false).build());
            observer.onCompleted();
        };

        StepVerifier.create(client.findById("prod-1", null))
                .assertNext(product -> assertFalse(product.active()))
                .verifyComplete();
    }

    /** product-service stand-in whose answer is set per test. */
    private static final class ScriptedCatalog extends ProductCatalogGrpc.ProductCatalogImplBase {

        private Consumer<StreamObserver<ProductReply>> answer = observer -> {
            observer.onNext(ProductReply.getDefaultInstance());
            observer.onCompleted();
        };

        private final AtomicReference<GetProductRequest> lastRequest = new AtomicReference<>();

        @Override
        public void getProduct(GetProductRequest request, StreamObserver<ProductReply> observer) {
            this.lastRequest.set(request);
            answer.accept(observer);
        }
    }
}
