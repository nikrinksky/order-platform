package com.orderplatform.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One reservation of an order item, learned from {@code inventory.reserved} events.
 *
 * <p>This is the reconciliation half of the reservation flow: the REST call to inventory is the
 * fast path, and the event is the truth. When the two disagree - a timed out call that actually
 * reserved - the event still moves the order forward.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "item_reservation",
        uniqueConstraints = @UniqueConstraint(name = "uk_item_reservation_event", columnNames = "event_id"),
        indexes = @Index(name = "idx_item_reservation_order", columnList = "order_id")
)
public class ItemReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Delivery id of the {@code inventory.reserved} record; a redelivery matches it here. */
    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
