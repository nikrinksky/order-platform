package com.orderplatform.order.service;

import com.orderplatform.order.dto.CreateOrderRequest;
import com.orderplatform.order.dto.OrderResponse;
import com.orderplatform.order.model.Order;
import com.orderplatform.order.model.OrderItem;
import com.orderplatform.order.model.Order.OrderStatus;
import com.orderplatform.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .userId(request.getUserId())
                .orderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .status(OrderStatus.NEW)
                .totalAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            OrderItem item = OrderItem.builder()
                    .productId(itemReq.getProductId())
                    .productName(itemReq.getProductName())
                    .quantity(itemReq.getQuantity())
                    .price(BigDecimal.ZERO)
                    .build();
            order.getItems().add(item);
        }

        order = orderRepository.save(order);
        log.info("Order {} created for user {}", order.getOrderNumber(), order.getUserId());

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse reserveOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.NEW) {
            throw new RuntimeException("Order can only be reserved in NEW status");
        }

        boolean allReserved = true;
        for (OrderItem item : order.getItems()) {
            boolean reserved = reserveInventory(item.getProductId(), item.getQuantity(), orderId);
            if (!reserved) {
                allReserved = false;
                break;
            }
        }

        if (allReserved) {
            order.setStatus(OrderStatus.RESERVED);
            order.setUpdatedAt(LocalDateTime.now());
            order = orderRepository.save(order);
            log.info("Order {} reserved", orderId);
        } else {
            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(LocalDateTime.now());
            order = orderRepository.save(order);
            log.warn("Order {} cancelled - insufficient inventory", orderId);
        }

        return OrderResponse.from(order);
    }

    private boolean reserveInventory(String productId, Integer quantity, String orderId) {
        try {
            String url = "http://inventory-service:8083/api/inventory/reserve";
            var request = java.util.Map.of(
                    "orderId", orderId,
                    "productId", productId,
                    "quantity", quantity
            );
            ResponseEntity<?> response = restTemplate.postForEntity(url, request, Object.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to reserve inventory for product {}: {}", productId, e.getMessage());
            return false;
        }
    }

    @Transactional
    public OrderResponse updateStatus(String orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());

        if (status == OrderStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
        } else if (status == OrderStatus.SHIPPED) {
            order.setShippedAt(LocalDateTime.now());
        }

        order = orderRepository.save(order);
        log.info("Order {} status updated to {}", orderId, status);

        return OrderResponse.from(order);
    }

    public OrderResponse findById(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        return OrderResponse.from(order);
    }

    public List<OrderResponse> findByUserId(String userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }
}
