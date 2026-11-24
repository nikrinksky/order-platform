package com.orderplatform.inventory.integration;

import com.orderplatform.grpc.inventory.InventoryServiceGrpc;
import com.orderplatform.grpc.inventory.ReserveRequest;
import com.orderplatform.grpc.inventory.ReserveResponse;
import com.orderplatform.inventory.AbstractInventoryDatabaseTest;
import com.orderplatform.inventory.InventoryServiceApplication;
import com.orderplatform.inventory.TestSecurityConfig;
import com.orderplatform.inventory.model.InventoryItem;
import com.orderplatform.inventory.service.InventoryService;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the real gRPC server and calls it over the network.
 *
 * <p>The unit tests cover the adapter and the interceptor in isolation; this one proves the wiring
 * Spring gRPC does for us: that the endpoint is discovered as a service, that the interceptor is
 * applied to it, and that the call reaches the database through the shared business logic.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = InventoryServiceApplication.class,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                "spring.kafka.enabled=false",
                "spring.security.enabled=false",
                // The integration-tests profile turns the server off; this test needs it on, on a
                // port of its own so a local run of the service does not collide with it.
                "spring.grpc.server.enabled=true",
                "spring.grpc.server.port=19095",
                "jwt.secret=" + InventoryGrpcIntegrationTest.SECRET
        })
@ActiveProfiles("integration-tests")
@Import(TestSecurityConfig.class)
class InventoryGrpcIntegrationTest extends AbstractInventoryDatabaseTest {

    static final String SECRET = "integration-test-secret-must-be-at-least-256-bits-long-hmac";

    private static final int PORT = 19095;

    private static ManagedChannel channel;

    @Autowired
    private InventoryService inventoryService;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeAll
    static void openChannel() {
        channel = ManagedChannelBuilder.forAddress("localhost", PORT).usePlaintext().build();
    }

    @AfterAll
    static void closeChannel() {
        channel.shutdownNow();
    }

    @Test
    void shouldRejectCallWithoutToken() {
        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                () -> blockingStub().reserve(reserveRequest("order-grpc-1", "prod-grpc-1", 1)));

        assertEquals(Status.Code.UNAUTHENTICATED, error.getStatus().getCode());
    }

    @Test
    void shouldRefuseReserveForRegularUser() {
        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                () -> stubWithToken(token("ROLE_USER"))
                        .reserve(reserveRequest("order-grpc-2", "prod-grpc-1", 1)));

        assertEquals(Status.Code.PERMISSION_DENIED, error.getStatus().getCode());
    }

    @Test
    void shouldReserveStockForAdmin() {
        inventoryService.createOrUpdate("prod-grpc-3", 10);

        ReserveResponse response = stubWithToken(token("ROLE_ADMIN"))
                .reserve(reserveRequest("order-grpc-3", "prod-grpc-3", 4));

        assertTrue(response.getReserved());
        assertEquals(4, response.getQuantity());
        InventoryItem item = inventoryService.findByProductId("prod-grpc-3").orElseThrow();
        assertEquals(4, item.getReserved());
    }

    private static InventoryServiceGrpc.InventoryServiceBlockingStub blockingStub() {
        return InventoryServiceGrpc.newBlockingStub(channel);
    }

    private static InventoryServiceGrpc.InventoryServiceBlockingStub stubWithToken(String token) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
        return blockingStub()
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static ReserveRequest reserveRequest(String orderId, String productId, int quantity) {
        return ReserveRequest.newBuilder()
                .setOrderId(orderId)
                .setProductId(productId)
                .setQuantity(quantity)
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
