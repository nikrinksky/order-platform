package com.orderplatform.inventory.grpc;

import com.orderplatform.grpc.inventory.CommitRequest;
import com.orderplatform.grpc.inventory.CommitResponse;
import com.orderplatform.grpc.inventory.InventoryServiceGrpc;
import com.orderplatform.grpc.inventory.ReleaseRequest;
import com.orderplatform.grpc.inventory.ReleaseResponse;
import com.orderplatform.grpc.inventory.ReserveRequest;
import com.orderplatform.grpc.inventory.ReserveResponse;
import com.orderplatform.inventory.dto.ReservationRequest;
import com.orderplatform.inventory.dto.ReservationResponse;
import com.orderplatform.inventory.service.InventoryService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

/**
 * The gRPC face of the stock operations order-service needs.
 *
 * <p>It is a thin adapter: the business rules, the idempotency and the locking live in
 * {@link InventoryService} and are shared with the HTTP endpoints, so the two transports cannot
 * disagree about what a reservation means.
 */
@Slf4j
@GrpcService(interceptors = JwtServerInterceptor.class)
@RequiredArgsConstructor
public class InventoryGrpcEndpoint extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryService inventoryService;

    @Override
    public void reserve(ReserveRequest request, StreamObserver<ReserveResponse> responseObserver) {
        try {
            ReservationResponse result = inventoryService.reserve(ReservationRequest.builder()
                    .orderId(request.getOrderId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .build());
            responseObserver.onNext(ReserveResponse.newBuilder()
                    .setReserved(result.isReserved())
                    .setQuantity(result.getQuantity())
                    .setMessage(nullToEmpty(result.getMessage()))
                    .build());
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            fail(responseObserver, e);
        }
    }

    @Override
    public void release(ReleaseRequest request, StreamObserver<ReleaseResponse> responseObserver) {
        try {
            ReservationResponse result = inventoryService.release(
                    request.getOrderId(), request.getProductId(), request.getQuantity());
            responseObserver.onNext(ReleaseResponse.newBuilder()
                    .setQuantity(result.getQuantity())
                    .setMessage(nullToEmpty(result.getMessage()))
                    .build());
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            fail(responseObserver, e);
        }
    }

    @Override
    public void commit(CommitRequest request, StreamObserver<CommitResponse> responseObserver) {
        try {
            com.orderplatform.inventory.dto.CommitResponse result = inventoryService.commit(request.getOrderId());
            responseObserver.onNext(CommitResponse.newBuilder()
                    .setCommitted(result.isCommitted())
                    .setItemsCommitted(result.getItemsCommitted())
                    .setMessage(nullToEmpty(result.getMessage()))
                    .build());
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            fail(responseObserver, e);
        }
    }

    /**
     * Turns a business exception into a gRPC status.
     *
     * <p>Without this the client sees {@code UNKNOWN} for everything and cannot tell a rejected
     * quantity from a broken database. The exception message is passed on only for the client's own
     * mistakes; an unexpected failure is logged and reported without its details.
     */
    private static void fail(StreamObserver<?> responseObserver, RuntimeException e) {
        if (e instanceof IllegalArgumentException) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
            return;
        }
        log.error("gRPC stock operation failed", e);
        responseObserver.onError(Status.INTERNAL
                .withDescription("Stock operation failed")
                .asRuntimeException());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
