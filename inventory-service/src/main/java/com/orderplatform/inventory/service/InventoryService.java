package com.orderplatform.inventory.service;

import com.orderplatform.events.inventory.InventoryReleasedEvent;
import com.orderplatform.events.inventory.InventoryReservedEvent;
import com.orderplatform.inventory.dto.ReservationRequest;
import com.orderplatform.inventory.dto.ReservationResponse;
import com.orderplatform.inventory.model.InventoryItem;
import com.orderplatform.inventory.repository.InventoryItemRepository;
import com.orderplatform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    /** Kafka topics this service publishes to. Kept here so no string is typed twice. */
    private static final String TOPIC_RESERVED = "inventory.reserved";
    private static final String TOPIC_RELEASED = "inventory.released";

    private final InventoryItemRepository inventoryItemRepository;
    private final OutboxService outboxService;

    public Optional<InventoryItem> findByProductId(String productId) {
        return inventoryItemRepository.findByProductId(productId);
    }

    @Transactional
    public ReservationResponse reserve(ReservationRequest request) {
        requirePositive(request.getQuantity(), "Reservation quantity");

        InventoryItem item = inventoryItemRepository.findByProductIdForUpdate(request.getProductId())
                .orElseGet(() -> createEmptyItem(request.getProductId()));

        int availableQuantity = item.getQuantity() - item.getReserved();
        if (availableQuantity >= request.getQuantity()) {
            item.setReserved(item.getReserved() + request.getQuantity());
            item.setUpdatedAt(LocalDateTime.now());
            inventoryItemRepository.save(item);

            log.info("Reserved {} units of product {} for order {}",
                    request.getQuantity(), request.getProductId(), request.getOrderId());
            // The event id names the reservation, so a retried order cannot reserve
            // the stock twice and emit the same event twice.
            outboxService.append(TOPIC_RESERVED, request.getOrderId(),
                    request.getOrderId() + ":RESERVED:" + request.getProductId(),
                    InventoryReservedEvent.newBuilder()
                            .setOrderId(request.getOrderId())
                            .setProductId(request.getProductId())
                            .setQuantity(request.getQuantity())
                            .build());
            return ReservationResponse.builder()
                    .orderId(request.getOrderId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .reserved(true)
                    .message("Reserved successfully")
                    .build();
        } else {
            log.warn("Insufficient inventory for product {}. Available: {}, Requested: {}",
                    request.getProductId(), availableQuantity, request.getQuantity());
            return ReservationResponse.builder()
                    .orderId(request.getOrderId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .reserved(false)
                    .message("Insufficient inventory. Available: " + availableQuantity)
                    .build();
        }
    }

    @Transactional
    public ReservationResponse release(String orderId, String productId, Integer quantity) {
        requirePositive(quantity, "Release quantity");

        InventoryItem item = inventoryItemRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("Inventory item not found for product: " + productId));

        item.setReserved(Math.max(0, item.getReserved() - quantity));
        item.setUpdatedAt(LocalDateTime.now());
        inventoryItemRepository.save(item);

        log.info("Released {} units of product {} for order {}", quantity, productId, orderId);
        // Releasing the same reservation twice is a real scenario (a retried compensation),
        // so the event id names the release and the second one is not stored.
        outboxService.append(TOPIC_RELEASED, orderId,
                orderId + ":RELEASED:" + productId,
                InventoryReleasedEvent.newBuilder()
                        .setOrderId(orderId)
                        .setProductId(productId)
                        .setQuantity(quantity)
                        .build());
        return ReservationResponse.builder()
                .orderId(orderId)
                .productId(productId)
                .quantity(quantity)
                .reserved(true)
                .message("Released successfully")
                .build();
    }

    @Transactional
    public InventoryItem createOrUpdate(String productId, Integer quantity) {
        requireNotNegative(quantity, "Stock quantity");

        InventoryItem item = inventoryItemRepository.findByProductIdForUpdate(productId)
                .orElseGet(() -> createEmptyItem(productId));

        item.setQuantity(quantity);
        item.setAvailable(quantity > 0);
        item.setUpdatedAt(LocalDateTime.now());
        inventoryItemRepository.save(item);
        
        log.info("Created/Updated inventory for product {}: quantity={}", productId, quantity);
        return item;
    }

    @Transactional
    public void updateQuantity(String productId, Integer quantity) {
        requireNotNegative(quantity, "Stock quantity");

        InventoryItem item = inventoryItemRepository.findByProductIdForUpdate(productId)
                .orElseGet(() -> createEmptyItem(productId));

        item.setQuantity(quantity);
        item.setAvailable(quantity > 0);
        item.setUpdatedAt(LocalDateTime.now());
        inventoryItemRepository.save(item);
    }

    /**
     * Builds the placeholder row used when a product has no stock record yet. It is inserted
     * before the read-modify-write continues, so the row exists for the pessimistic lock of
     * every concurrent request to serialise on.
     */
    private InventoryItem createEmptyItem(String productId) {
        return inventoryItemRepository.save(InventoryItem.builder()
                .id(java.util.UUID.randomUUID().toString())
                .productId(productId)
                .quantity(0)
                .reserved(0)
                .available(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }

    /**
     * Rejects a non-positive reservation or release amount.
     *
     * <p>Without this a negative amount inverted the arithmetic: reserving -1000 turned the
     * reserved counter negative, and a following reservation of 500 passed against a stock of 1.
     */
    private static void requirePositive(Integer quantity, String what) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(what + " must be greater than zero");
        }
    }

    /** Rejects a negative stock amount; zero is a valid "out of stock" value. */
    private static void requireNotNegative(Integer quantity, String what) {
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException(what + " must not be negative");
        }
    }
}
