package com.orderplatform.product.integration;

import com.orderplatform.grpc.product.GetProductRequest;
import com.orderplatform.grpc.product.ProductCatalogGrpc;
import com.orderplatform.grpc.product.ProductReply;
import com.orderplatform.product.AbstractProductIntegrationTest;
import com.orderplatform.product.model.Product;
import com.orderplatform.product.repository.ProductRepository;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the real gRPC server and calls it over the network.
 *
 * <p>The unit tests cover the endpoint and the interceptor in isolation; this one proves the wiring
 * Spring gRPC does for us: that the endpoint is discovered as a service, that the interceptor is
 * applied to it, and that the call reaches MongoDB through the shared business logic - including the
 * visibility rule that hides an unpublished product from a visitor.
 */
class ProductGrpcIntegrationTest extends AbstractProductIntegrationTest {

    private static final String SECRET = "integration-test-secret-must-be-at-least-256-bits-long-hmac";

    private static final int PORT = 19096;

    private static ManagedChannel channel;

    @Autowired
    private ProductRepository productRepository;

    /**
     * The integration-tests profile turns the server off so the REST suite does not bind the port;
     * this test needs it on, on a port of its own so a local run of the service does not collide.
     */
    @DynamicPropertySource
    static void enableGrpcServer(DynamicPropertyRegistry registry) {
        registry.add("spring.grpc.server.enabled", () -> "true");
        registry.add("spring.grpc.server.port", () -> PORT);
        registry.add("jwt.secret", () -> SECRET);
    }

    @BeforeAll
    static void openChannel() {
        channel = ManagedChannelBuilder.forAddress("localhost", PORT).usePlaintext().build();
    }

    @AfterAll
    static void closeChannel() {
        channel.shutdownNow();
    }

    @BeforeEach
    void clearCatalogue() {
        productRepository.deleteAll();
    }

    @Test
    void shouldReturnPublishedProductToAnonymousVisitor() {
        productRepository.save(product("prod-grpc-1", "Phone", true));

        ProductReply reply = blockingStub().getProduct(request("prod-grpc-1"));

        assertEquals("prod-grpc-1", reply.getId());
        assertEquals("Phone", reply.getName());
        assertEquals("Description of Phone", reply.getDescription());
        // The price travels as a string, not as a double: 19.99 has no exact binary form.
        assertEquals("19.99", reply.getPrice());
        assertEquals("Electronics", reply.getCategory());
        assertEquals(10, reply.getQuantity());
        assertTrue(reply.getActive());
        assertEquals("2026-01-02T03:04:05", reply.getCreatedAt());
        assertEquals("2026-01-02T03:04:06", reply.getUpdatedAt());
    }

    /**
     * The catalogue is public, so an unpublished product must look missing to a visitor - the same
     * answer the REST endpoint gives.
     */
    @Test
    void shouldHideUnpublishedProductFromAnonymousVisitor() {
        productRepository.save(product("hidden-grpc-1", "Draft", false));

        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                () -> blockingStub().getProduct(request("hidden-grpc-1")));

        assertEquals(Status.Code.NOT_FOUND, error.getStatus().getCode());
    }

    @Test
    void shouldShowUnpublishedProductToAdministrator() {
        productRepository.save(product("hidden-grpc-2", "Draft", false));

        ProductReply reply = stubWithToken(token("ROLE_ADMIN")).getProduct(request("hidden-grpc-2"));

        assertEquals("hidden-grpc-2", reply.getId());
        assertFalse(reply.getActive());
    }

    @Test
    void shouldReportUnknownProductAsNotFound() {
        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                () -> blockingStub().getProduct(request("missing-grpc-1")));

        assertEquals(Status.Code.NOT_FOUND, error.getStatus().getCode());
        assertTrue(error.getStatus().getDescription().contains("missing-grpc-1"));
    }

    /**
     * A malformed token is not a rejection here: the catalogue stays readable, the caller just does
     * not get the privileged view.
     */
    @Test
    void shouldTreatAnInvalidTokenAsAnonymous() {
        productRepository.save(product("prod-grpc-2", "Phone", true));

        ProductReply reply = stubWithToken("not-a-jwt").getProduct(request("prod-grpc-2"));

        assertEquals("prod-grpc-2", reply.getId());
    }

    /**
     * The identity lives in the gRPC context for one call only: a privileged call must not leave the
     * security context populated for the next call served by the same thread.
     */
    @Test
    void shouldNotLeakTheAdministratorIdentityIntoTheNextCall() {
        productRepository.save(product("hidden-grpc-3", "Draft", false));

        ProductReply adminReply = stubWithToken(token("ROLE_ADMIN")).getProduct(request("hidden-grpc-3"));
        assertEquals("hidden-grpc-3", adminReply.getId());

        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                () -> blockingStub().getProduct(request("hidden-grpc-3")));
        assertEquals(Status.Code.NOT_FOUND, error.getStatus().getCode());
    }

    private static ProductCatalogGrpc.ProductCatalogBlockingStub blockingStub() {
        return ProductCatalogGrpc.newBlockingStub(channel);
    }

    private static ProductCatalogGrpc.ProductCatalogBlockingStub stubWithToken(String token) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
        return blockingStub().withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static GetProductRequest request(String id) {
        return GetProductRequest.newBuilder().setId(id).build();
    }

    private static Product product(String id, String name, boolean active) {
        return Product.builder()
                .id(id)
                .name(name)
                .description("Description of " + name)
                .price(new BigDecimal("19.99"))
                .category("Electronics")
                .quantity(10)
                .active(active)
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5))
                .updatedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 6))
                .build();
    }

    private static String token(String role) {
        return Jwts.builder()
                .setSubject("integration-test")
                .claim("token_use", "access")
                .claim("roles", List.of(role))
                .setExpiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
    }
}
