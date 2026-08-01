package com.orderplatform.inventory.controller;

import com.orderplatform.inventory.dto.ReservationRequest;
import com.orderplatform.inventory.dto.ReservationResponse;
import com.orderplatform.inventory.model.InventoryItem;
import com.orderplatform.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/product/{productId}")
    public ResponseEntity<InventoryItem> getInventoryByProduct(@PathVariable("productId") String productId) {
        Optional<InventoryItem> item = inventoryService.findByProductId(productId);
        return item.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reserve")
    public ResponseEntity<ReservationResponse> reserve(@RequestBody ReservationRequest request) {
        return ResponseEntity.ok(inventoryService.reserve(request));
    }

    @PostMapping(consumes = "*/*")
    public ResponseEntity<InventoryItem> createInventoryItem(
            @RequestParam("productId") String productId,
            @RequestParam("quantity") Integer quantity) {
        return ResponseEntity.ok(inventoryService.createOrUpdate(productId, quantity));
    }

    @PostMapping("/release")
    public ResponseEntity<ReservationResponse> release(
            @RequestParam String orderId,
            @RequestParam String productId,
            @RequestParam Integer quantity) {
        return ResponseEntity.ok(inventoryService.release(orderId, productId, quantity));
    }

    @PatchMapping("/product/{productId}/quantity")
    public ResponseEntity<Void> updateQuantity(
            @PathVariable("productId") String productId,
            @RequestParam Integer quantity) {
        inventoryService.updateQuantity(productId, quantity);
        return ResponseEntity.ok().build();
    }
}
