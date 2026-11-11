package com.orderplatform.inventory.service;

import com.orderplatform.events.inventory.InventoryReleasedEvent;
import com.orderplatform.events.inventory.InventoryReservedEvent;
import com.orderplatform.inventory.dto.ReservationRequest;
import com.orderplatform.inventory.dto.ReservationResponse;
import com.orderplatform.inventory.model.InventoryItem;
import com.orderplatform.inventory.model.Reservation;
import com.orderplatform.inventory.model.ReservationStatus;
import com.orderplatform.inventory.repository.InventoryItemRepository;
import com.orderplatform.inventory.repository.ReservationRepository;
import com.orderplatform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    /** Kafka topics this service publishes to. Kept here so no string is typed twice. */
    private static final String TOPIC_RESERVED = "inventory.reserved";
    private static final String TOPIC_RELEASED = "inventory.released";

    private final InventoryItemRepository inventoryItemRepository;
    private final ReservationRepository reservationRepository;
    private final OutboxService outboxService;

    public Optional<InventoryItem> findByProductId(String productId) {
        return inventoryItemRepository.findByProductId(productId);
    }

    /**
     * Holds stock for an order.
     *
     * <p>Idempotent per {@code (orderId, productId)}: the reservation is a row, not just an
     * increment of the {@code reserved} counter. A repeated call finds that row and answers with
     * the quantity that is already held, so a retried request cannot reserve the same stock twice.
     *
     * <p>The inventory row is locked first, so every reservation of a product is serialised; the
     * reservation lookup then runs under that lock and cannot race with a concurrent one.
     *
     * @param request order, product and quantity to hold
     * @return the outcome; {@code reserved} is false when the stock is insufficient or the order
     *         already gave its hold back
     */
    @Transactional
    public ReservationResponse reserve(ReservationRequest request) {
        requirePositive(request.getQuantity(), "Reservation quantity");

        InventoryItem item = inventoryItemRepository.findByProductIdForUpdate(request.getProductId())
                .orElseGet(() -> createEmptyItem(request.getProductId()));

        Optional<Reservation> existing = reservationRepository
                .findByOrderIdAndProductId(request.getOrderId(), request.getProductId());
        if (existing.isPresent()) {
            return answerForExisting(existing.get(), request);
        }

        int availableQuantity = item.getQuantity() - item.getReserved();
        if (availableQuantity < request.getQuantity()) {
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

        item.setReserved(item.getReserved() + request.getQuantity());
        item.setUpdatedAt(LocalDateTime.now());
        inventoryItemRepository.save(item);

        LocalDateTime now = LocalDateTime.now();
        reservationRepository.save(Reservation.builder()
                .id(UUID.randomUUID().toString())
                .orderId(request.getOrderId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .status(ReservationStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());

        log.info("Reserved {} units of product {} for order {}",
                request.getQuantity(), request.getProductId(), request.getOrderId());
        // The event id names the reservation, so a retried order cannot emit the same event twice.
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
    }

    /**
     * Answers a reserve call for an order that already reserved this product.
     *
     * <p>An {@code ACTIVE} hold is the successful outcome of the first call, so the retry reports
     * the same thing without touching the stock. A hold that was released or committed is not
     * resurrected: the order gave the stock back or the goods already shipped, and silently
     * reserving again would hold stock for an order that no longer needs it.
     */
    private ReservationResponse answerForExisting(Reservation reservation, ReservationRequest request) {
        if (reservation.isActive()) {
            log.info("Reservation for order {} and product {} is already active, answering idempotently",
                    request.getOrderId(), request.getProductId());
            return ReservationResponse.builder()
                    .orderId(request.getOrderId())
                    .productId(request.getProductId())
                    .quantity(reservation.getQuantity())
                    .reserved(true)
                    .message("Already reserved")
                    .build();
        }
        log.warn("Reservation for order {} and product {} is {}, refusing to reserve again",
                request.getOrderId(), request.getProductId(), reservation.getStatus());
        return ReservationResponse.builder()
                .orderId(request.getOrderId())
                .productId(request.getProductId())
                .quantity(reservation.getQuantity())
                .reserved(false)
                .message("Reservation is already " + reservation.getStatus())
                .build();
    }

    /**
     * Gives back the stock an order holds for a product.
     *
     * <p>Idempotent per {@code (orderId, productId)} and scoped to that order: the amount given
     * back is the amount this order reserved, read from its own row. The old code subtracted the
     * caller's quantity from the aggregate, so a repeated release - or a release carrying the
     * wrong number - could free the reservation of a different order.
     *
     * <p>A release with nothing to give back is a success, not an error: a retried compensation
     * must not fail the operation it compensates.
     *
     * @param orderId   the order whose hold is released
     * @param productId the product
     * @param quantity  units the caller believes are held; only validated, the stored amount wins
     * @return the outcome; {@code quantity} is what was actually given back
     */
    @Transactional
    public ReservationResponse release(String orderId, String productId, Integer quantity) {
        requirePositive(quantity, "Release quantity");

        Optional<Reservation> found = reservationRepository.findByOrderIdAndProductId(orderId, productId);
        if (found.isEmpty()) {
            log.info("No reservation for order {} and product {}, nothing to release", orderId, productId);
            return released(orderId, productId, quantity, "No reservation to release");
        }

        InventoryItem item = inventoryItemRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new IllegalStateException(
                        "Reservation exists for product " + productId + " but its inventory row is missing"));

        // Re-read under the lock: a concurrent release of the same order may have won the race.
        Reservation reservation = reservationRepository
                .findByOrderIdAndProductIdForUpdate(orderId, productId)
                .orElseThrow(() -> new IllegalStateException(
                        "Reservation disappeared for order " + orderId + " and product " + productId));
        if (!reservation.isActive()) {
            log.info("Reservation for order {} and product {} is already {}, nothing to release",
                    orderId, productId, reservation.getStatus());
            return released(orderId, productId, reservation.getQuantity(),
                    "Reservation is already " + reservation.getStatus());
        }

        int releasedQuantity = reservation.getQuantity();
        if (quantity != releasedQuantity) {
            log.warn("Release for order {} and product {} asked for {} units but the order holds {}; " +
                    "releasing the held amount", orderId, productId, quantity, releasedQuantity);
        }

        item.setReserved(Math.max(0, item.getReserved() - releasedQuantity));
        item.setUpdatedAt(LocalDateTime.now());
        inventoryItemRepository.save(item);

        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        log.info("Released {} units of product {} for order {}", releasedQuantity, productId, orderId);
        // Releasing the same reservation twice is a real scenario (a retried compensation),
        // so the event id names the release and the second one is not stored.
        outboxService.append(TOPIC_RELEASED, orderId,
                orderId + ":RELEASED:" + productId,
                InventoryReleasedEvent.newBuilder()
                        .setOrderId(orderId)
                        .setProductId(productId)
                        .setQuantity(releasedQuantity)
                        .build());
        return released(orderId, productId, releasedQuantity, "Released successfully");
    }

    private static ReservationResponse released(String orderId, String productId, int quantity,
                                                String message) {
        return ReservationResponse.builder()
                .orderId(orderId)
                .productId(productId)
                .quantity(quantity)
                .reserved(true)
                .message(message)
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
