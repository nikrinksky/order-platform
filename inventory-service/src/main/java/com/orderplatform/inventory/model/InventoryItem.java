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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
