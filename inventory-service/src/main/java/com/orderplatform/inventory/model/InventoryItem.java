package com.orderplatform.inventory.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer reserved;

    @Column(nullable = false)
    private Boolean available;

    /**
     * Optimistic-lock counter. Guards against a lost update when the row is modified outside the
     * pessimistic lock (for example two stock updates racing after the same read).
     */
    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * Free stock, i.e. what is left after the reservations.
     *
     * <p>Serialised as {@code availableQuantity} and read by order-service when it checks whether
     * an order can be created. It is derived, never stored: without it the check had no field to
     * read and every order passed the pre-check regardless of the actual stock.
     *
     * @return quantity minus reserved
     */
    public int getAvailableQuantity() {
        return (quantity == null ? 0 : quantity) - (reserved == null ? 0 : reserved);
    }
}
