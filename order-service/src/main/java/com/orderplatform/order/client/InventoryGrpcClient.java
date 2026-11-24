package com.orderplatform.order.client;

import com.orderplatform.grpc.inventory.CommitRequest;
import com.orderplatform.grpc.inventory.CommitResponse;
import com.orderplatform.grpc.inventory.InventoryServiceGrpc;
import com.orderplatform.grpc.inventory.ReleaseRequest;
import com.orderplatform.grpc.inventory.ReserveRequest;
import com.orderplatform.grpc.inventory.ReserveResponse;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link InventoryClient} over gRPC.
 *
 * <p>The call is typed end to end: the request and the answer come from the shared contract, so a
 * renamed field is a compile error instead of a {@code null} read from a {@code Map}. The failure
 * policy is the same as the REST implementation it replaces: a reserve or a commit that does not
 * come back confirmed is a failure, a release is best-effort.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryGrpcClient implements InventoryClient {

    private final InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    @Override
    public boolean reserve(String productId, int quantity, String orderId) {
        try {
            ReserveResponse response = inventoryStub.reserve(ReserveRequest.newBuilder()
                    .setOrderId(orderId)
                    .setProductId(productId)
                    .setQuantity(quantity)
                    .build());
            if (!response.getReserved()) {
                log.warn("Order {} - inventory-service did not reserve {} units of {}: {}",
                        orderId, quantity, productId, response.getMessage());
            }
            return response.getReserved();
        } catch (StatusRuntimeException e) {
            log.error("Order {} - reserve call to inventory-service failed: {}",
                    orderId, e.getStatus(), e);
            return false;
        }
    }

    @Override
    public void release(String productId, int quantity, String orderId) {
        try {
            inventoryStub.release(ReleaseRequest.newBuilder()
                    .setOrderId(orderId)
                    .setProductId(productId)
                    .setQuantity(quantity)
                    .build());
        } catch (StatusRuntimeException e) {
            // Best-effort: the order is already cancelled, and the hold is reconciled by the
            // inventory.reserved/released events or by a repeated compensation.
            log.warn("Order {} - release call to inventory-service failed: {}",
                    orderId, e.getStatus());
        }
    }

    @Override
    public boolean commit(String orderId) {
        try {
            CommitResponse response = inventoryStub.commit(CommitRequest.newBuilder()
                    .setOrderId(orderId)
                    .build());
            if (!response.getCommitted()) {
                log.warn("Order {} - inventory-service did not confirm the stock write-off: {}",
                        orderId, response.getMessage());
            }
            return response.getCommitted();
        } catch (StatusRuntimeException e) {
            log.error("Order {} - commit call to inventory-service failed: {}",
                    orderId, e.getStatus(), e);
            return false;
        }
    }
}
