package com.orderplatform.order.controller;

import com.orderplatform.order.dto.CreateOrderRequest;
import com.orderplatform.order.dto.OrderResponse;
import com.orderplatform.order.model.Order.OrderStatus;
import com.orderplatform.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        request.setUserId(com.orderplatform.common.security.SecurityUtils.getCurrentUserId());
        return ResponseEntity.status(201).body(orderService.createOrder(request));
    }

    @PostMapping("/{orderId}/reserve")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_MANAGER')")
    public ResponseEntity<OrderResponse> reserveOrder(@PathVariable("orderId") String orderId) {
        return ResponseEntity.ok(orderService.reserveOrder(orderId));
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_MANAGER')")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable("orderId") String orderId,
            @RequestParam OrderStatus status) {
        return ResponseEntity.ok(orderService.updateStatus(orderId, status));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('ROLE_ADMIN') or @orderSecurity.isOwner(#orderId, authentication.name)")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable("orderId") String orderId) {
        return ResponseEntity.ok(orderService.findById(orderId));
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN') or #userId == authentication.name")
    public ResponseEntity<List<OrderResponse>> getOrdersByUserId(@RequestParam("userId") String userId) {
        return ResponseEntity.ok(orderService.findByUserId(userId));
    }
}
