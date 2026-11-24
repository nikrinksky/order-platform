package com.orderplatform.order.client;

import com.orderplatform.grpc.inventory.CommitRequest;
import com.orderplatform.grpc.inventory.CommitResponse;
import com.orderplatform.grpc.inventory.InventoryServiceGrpc;
import com.orderplatform.grpc.inventory.ReleaseRequest;
import com.orderplatform.grpc.inventory.ReleaseResponse;
import com.orderplatform.grpc.inventory.ReserveRequest;
import com.orderplatform.grpc.inventory.ReserveResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link InventoryGrpcClient} against a real gRPC server running in the same JVM.
 *
 * <p>The client is what decides whether an order is considered reserved, so the tests drive the
 * generated contract end to end instead of mocking the stub: an empty or unexpected answer has to
 * be treated as "not reserved", and an unavailable server must not be mistaken for success.
 */
class InventoryGrpcClientTest {

    private final ScriptedInventoryService service = new ScriptedInventoryService();
    private Server server;
    private ManagedChannel channel;
    private InventoryGrpcClient client;

    @BeforeEach
    void startServer() throws IOException {
        String name = "inventory-" + UUID.randomUUID();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        client = new InventoryGrpcClient(InventoryServiceGrpc.newBlockingStub(channel));
    }

    @AfterEach
    void stopServer() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void reserveShouldReturnTrueWhenInventoryConfirms() {
        service.reserveAnswer = observer -> {
            observer.onNext(ReserveResponse.newBuilder().setReserved(true).setQuantity(2).build());
            observer.onCompleted();
        };

        assertTrue(client.reserve("prod-1", 2, "order-1"));
        assertEquals("prod-1", service.lastReserve.getProductId());
        assertEquals(2, service.lastReserve.getQuantity());
        assertEquals("order-1", service.lastReserve.getOrderId());
    }

    @Test
    void reserveShouldReturnFalseWhenStockIsInsufficient() {
        service.reserveAnswer = observer -> {
            observer.onNext(ReserveResponse.newBuilder().setReserved(false)
                    .setMessage("Insufficient inventory. Available: 0").build());
            observer.onCompleted();
        };

        assertFalse(client.reserve("prod-1", 5, "order-1"));
    }

    /**
     * A refusal has to fail the reservation: an order created without stock is exactly the bug the
     * fail-closed policy exists to prevent.
     */
    @Test
    void reserveShouldReturnFalseWhenServerRefusesTheCall() {
        service.reserveAnswer = observer -> observer.onError(Status.PERMISSION_DENIED
                .withDescription("requires ROLE_ADMIN").asRuntimeException());

        assertFalse(client.reserve("prod-1", 1, "order-1"));
    }

    @Test
    void reserveShouldReturnFalseWhenServerIsUnavailable() {
        channel.shutdownNow();

        assertFalse(client.reserve("prod-1", 1, "order-1"));
    }

    @Test
    void releaseShouldSendTheHoldBack() {
        service.releaseAnswer = observer -> {
            observer.onNext(ReleaseResponse.newBuilder().setQuantity(2).setMessage("Released").build());
            observer.onCompleted();
        };

        client.release("prod-1", 2, "order-1");

        assertEquals("order-1", service.lastRelease.getOrderId());
        assertEquals("prod-1", service.lastRelease.getProductId());
        assertEquals(2, service.lastRelease.getQuantity());
    }

    /**
     * Releasing is best-effort: the order is already cancelled, so a failing call must not travel
     * back to the caller and break the cancellation that triggered it.
     */
    @Test
    void releaseShouldSwallowServerErrors() {
        service.releaseAnswer = observer -> observer.onError(Status.UNAVAILABLE.asRuntimeException());

        client.release("prod-1", 2, "order-1");
    }

    @Test
    void commitShouldReturnTrueWhenStockIsWrittenOff() {
        service.commitAnswer = observer -> {
            observer.onNext(CommitResponse.newBuilder().setCommitted(true).setItemsCommitted(1).build());
            observer.onCompleted();
        };

        assertTrue(client.commit("order-1"));
        assertEquals("order-1", service.lastCommit.getOrderId());
    }

    @Test
    void commitShouldReturnFalseWhenNotConfirmed() {
        service.commitAnswer = observer -> {
            observer.onNext(CommitResponse.newBuilder().setCommitted(false)
                    .setMessage("No active reservation to commit").build());
            observer.onCompleted();
        };

        assertFalse(client.commit("order-1"));
    }

    @Test
    void commitShouldReturnFalseWhenServerFails() {
        service.commitAnswer = observer -> observer.onError(Status.INTERNAL.asRuntimeException());

        assertFalse(client.commit("order-1"));
    }

    /** Inventory-service stand-in whose answers are set per test. */
    private static final class ScriptedInventoryService extends InventoryServiceGrpc.InventoryServiceImplBase {

        private Consumer<StreamObserver<ReserveResponse>> reserveAnswer = observer -> {
            observer.onNext(ReserveResponse.getDefaultInstance());
            observer.onCompleted();
        };
        private Consumer<StreamObserver<ReleaseResponse>> releaseAnswer = observer -> {
            observer.onNext(ReleaseResponse.getDefaultInstance());
            observer.onCompleted();
        };
        private Consumer<StreamObserver<CommitResponse>> commitAnswer = observer -> {
            observer.onNext(CommitResponse.getDefaultInstance());
            observer.onCompleted();
        };

        private ReserveRequest lastReserve;
        private ReleaseRequest lastRelease;
        private CommitRequest lastCommit;

        @Override
        public void reserve(ReserveRequest request, StreamObserver<ReserveResponse> observer) {
            this.lastReserve = request;
            reserveAnswer.accept(observer);
        }

        @Override
        public void release(ReleaseRequest request, StreamObserver<ReleaseResponse> observer) {
            this.lastRelease = request;
            releaseAnswer.accept(observer);
        }

        @Override
        public void commit(CommitRequest request, StreamObserver<CommitResponse> observer) {
            this.lastCommit = request;
            commitAnswer.accept(observer);
        }
    }
}
