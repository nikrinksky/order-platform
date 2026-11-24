package com.orderplatform.inventory.grpc;

import com.orderplatform.grpc.inventory.CommitRequest;
import com.orderplatform.grpc.inventory.CommitResponse;
import com.orderplatform.grpc.inventory.ReleaseRequest;
import com.orderplatform.grpc.inventory.ReleaseResponse;
import com.orderplatform.grpc.inventory.ReserveRequest;
import com.orderplatform.grpc.inventory.ReserveResponse;
import com.orderplatform.inventory.dto.ReservationRequest;
import com.orderplatform.inventory.dto.ReservationResponse;
import com.orderplatform.inventory.service.InventoryService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the gRPC adapter.
 *
 * <p>The adapter must translate both the successful answers and the exceptions of
 * {@link InventoryService}: an exception that escapes becomes {@code UNKNOWN} on the client, which
 * hides whether the stock was rejected or the database broke.
 */
@ExtendWith(MockitoExtension.class)
class InventoryGrpcEndpointTest {

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InventoryGrpcEndpoint endpoint;

    @Test
    void reserveShouldReturnConfirmedReservation() {
        when(inventoryService.reserve(any(ReservationRequest.class))).thenReturn(
                ReservationResponse.builder().quantity(3).reserved(true).message("Reserved").build());
        CapturingObserver<ReserveResponse> observer = new CapturingObserver<>();

        endpoint.reserve(ReserveRequest.newBuilder()
                .setOrderId("order-1").setProductId("prod-1").setQuantity(3).build(), observer);

        assertTrue(observer.completed);
        assertEquals(1, observer.values.size());
        assertTrue(observer.values.get(0).getReserved());
        assertEquals(3, observer.values.get(0).getQuantity());
        assertEquals("Reserved", observer.values.get(0).getMessage());
    }

    @Test
    void reserveShouldPassTheRequestThrough() {
        when(inventoryService.reserve(any(ReservationRequest.class))).thenReturn(
                ReservationResponse.builder().quantity(1).reserved(true).build());
        CapturingObserver<ReserveResponse> observer = new CapturingObserver<>();
        ArgumentCaptor<ReservationRequest> captor = ArgumentCaptor.forClass(ReservationRequest.class);

        endpoint.reserve(ReserveRequest.newBuilder()
                .setOrderId("order-1").setProductId("prod-1").setQuantity(2).build(), observer);

        verify(inventoryService).reserve(captor.capture());
        assertEquals("order-1", captor.getValue().getOrderId());
        assertEquals("prod-1", captor.getValue().getProductId());
        assertEquals(2, captor.getValue().getQuantity());
    }

    @Test
    void reserveShouldReportInsufficientStockAsAnAnswerNotAnError() {
        when(inventoryService.reserve(any(ReservationRequest.class))).thenReturn(
                ReservationResponse.builder().quantity(5).reserved(false).message("Insufficient").build());
        CapturingObserver<ReserveResponse> observer = new CapturingObserver<>();

        endpoint.reserve(ReserveRequest.newBuilder()
                .setOrderId("order-1").setProductId("prod-1").setQuantity(5).build(), observer);

        assertTrue(observer.completed);
        assertFalse(observer.values.get(0).getReserved());
    }

    @Test
    void reserveShouldMapBadQuantityToInvalidArgument() {
        when(inventoryService.reserve(any(ReservationRequest.class)))
                .thenThrow(new IllegalArgumentException("Reservation quantity must be greater than zero"));
        CapturingObserver<ReserveResponse> observer = new CapturingObserver<>();

        endpoint.reserve(ReserveRequest.newBuilder()
                .setOrderId("order-1").setProductId("prod-1").setQuantity(-1).build(), observer);

        assertEquals(Status.Code.INVALID_ARGUMENT, statusOf(observer).getStatus().getCode());
    }

    @Test
    void reserveShouldMapUnexpectedFailureToInternalWithoutLeakingDetails() {
        when(inventoryService.reserve(any(ReservationRequest.class)))
                .thenThrow(new IllegalStateException("connection pool exhausted"));
        CapturingObserver<ReserveResponse> observer = new CapturingObserver<>();

        endpoint.reserve(ReserveRequest.newBuilder()
                .setOrderId("order-1").setProductId("prod-1").setQuantity(1).build(), observer);

        StatusRuntimeException error = statusOf(observer);
        assertEquals(Status.Code.INTERNAL, error.getStatus().getCode());
        assertEquals("Stock operation failed", error.getStatus().getDescription());
    }

    @Test
    void releaseShouldReturnReleasedQuantity() {
        when(inventoryService.release("order-1", "prod-1", 2)).thenReturn(
                ReservationResponse.builder().quantity(2).reserved(true).message("Released").build());
        CapturingObserver<ReleaseResponse> observer = new CapturingObserver<>();

        endpoint.release(ReleaseRequest.newBuilder()
                .setOrderId("order-1").setProductId("prod-1").setQuantity(2).build(), observer);

        assertTrue(observer.completed);
        assertEquals(2, observer.values.get(0).getQuantity());
        assertEquals("Released", observer.values.get(0).getMessage());
    }

    @Test
    void releaseShouldMapBadQuantityToInvalidArgument() {
        when(inventoryService.release(anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Release quantity must be greater than zero"));
        CapturingObserver<ReleaseResponse> observer = new CapturingObserver<>();

        endpoint.release(ReleaseRequest.newBuilder()
                .setOrderId("order-1").setProductId("prod-1").setQuantity(0).build(), observer);

        assertEquals(Status.Code.INVALID_ARGUMENT, statusOf(observer).getStatus().getCode());
    }

    @Test
    void commitShouldReturnWrittenOffReservations() {
        when(inventoryService.commit("order-1")).thenReturn(
                com.orderplatform.inventory.dto.CommitResponse.builder()
                        .orderId("order-1").committed(true).itemsCommitted(2).message("Committed").build());
        CapturingObserver<CommitResponse> observer = new CapturingObserver<>();

        endpoint.commit(CommitRequest.newBuilder().setOrderId("order-1").build(), observer);

        assertTrue(observer.completed);
        assertTrue(observer.values.get(0).getCommitted());
        assertEquals(2, observer.values.get(0).getItemsCommitted());
    }

    @Test
    void commitShouldMapUnexpectedFailureToInternal() {
        when(inventoryService.commit(anyString())).thenThrow(new IllegalStateException("lock timeout"));
        CapturingObserver<CommitResponse> observer = new CapturingObserver<>();

        endpoint.commit(CommitRequest.newBuilder().setOrderId("order-1").build(), observer);

        assertEquals(Status.Code.INTERNAL, statusOf(observer).getStatus().getCode());
    }

    /** The gRPC status a failed call reports; fails the test when the call actually succeeded. */
    private static StatusRuntimeException statusOf(CapturingObserver<?> observer) {
        assertNotNull(observer.error, "the call was expected to fail");
        return assertInstanceOf(StatusRuntimeException.class, observer.error);
    }

    /** Minimal observer that records what the adapter answered. */
    private static final class CapturingObserver<T> implements StreamObserver<T> {

        private final List<T> values = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
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
