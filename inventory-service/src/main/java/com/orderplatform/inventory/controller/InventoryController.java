package com.orderplatform.inventory.controller;

import com.orderplatform.inventory.dto.ReservationRequest;
import com.orderplatform.inventory.dto.ReservationResponse;
import com.orderplatform.inventory.model.InventoryItem;
import com.orderplatform.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/product/{productId}")
    public ResponseEntity<InventoryItem> getInventoryByProduct(@PathVariable("productId") String productId) {
        Optional<InventoryItem> item = inventoryService.findByProductId(productId);
        return item.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Reserves stock for an order. Callable by an administrator or a manager only - it used to be
     * open to anonymous callers, which let anyone take stock out of circulation.
     */
    @PostMapping("/reserve")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.ok(inventoryService.reserve(request));
    }

    @PostMapping(consumes = "*/*")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InventoryItem> createInventoryItem(
            @RequestParam("productId") String productId,
            @RequestParam("quantity") @PositiveOrZero(message = "quantity must not be negative") Integer quantity) {
        return ResponseEntity.ok(inventoryService.createOrUpdate(productId, quantity));
    }

    /**
     * Releases a reservation. Callable by an administrator or a manager only - it used to be open
     * to anonymous callers.
     */
    @PostMapping("/release")
    public ResponseEntity<ReservationResponse> release(
            @RequestParam String orderId,
            @RequestParam String productId,
            @RequestParam @Positive(message = "quantity must be greater than zero") Integer quantity) {
        return ResponseEntity.ok(inventoryService.release(orderId, productId, quantity));
    }

    @PatchMapping("/product/{productId}/quantity")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> updateQuantity(
            @PathVariable("productId") String productId,
            @RequestParam @PositiveOrZero(message = "quantity must not be negative") Integer quantity) {
        inventoryService.updateQuantity(productId, quantity);
        return ResponseEntity.ok().build();
    }
}
