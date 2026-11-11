package com.orderplatform.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One reservation of one product for one order.
 *
 * <p>This row is the record of what an order actually holds. The {@code reserved} counter on
 * {@link InventoryItem} stays the aggregate that the availability check reads, but it is no longer
 * the only trace of a hold: without a per-order row, {@code release} could only subtract a number
 * from the aggregate, so a repeated release drained the reservation of a different order and a
 * repeated reserve reserved the same stock twice.
 *
 * <p>{@code (orderId, productId)} is unique: an order holds at most one reservation per product,
 * and that pair is the natural idempotency key of both endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reservations", uniqueConstraints =
        @UniqueConstraint(name = "uk_reservation_order_product", columnNames = {"orderId", "productId"}))
public class Reservation {

    @Id
    private String id;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String productId;

    /** Units held when the reservation was made; a release gives back exactly this much. */
    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** Whether the hold is still in effect. */
    public boolean isActive() {
        return status == ReservationStatus.ACTIVE;
    }
}
