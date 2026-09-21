package com.orderplatform.order.service;

import com.orderplatform.order.client.InventoryClient;
import com.orderplatform.order.dto.CreateOrderRequest;
import com.orderplatform.order.dto.OrderResponse;
import com.orderplatform.order.model.IdempotencyKey;
import com.orderplatform.order.model.Order;
import com.orderplatform.order.model.OrderItem;
import com.orderplatform.order.model.Order.OrderStatus;
import com.orderplatform.order.model.OrderStatusTransitions;
import com.orderplatform.order.repository.IdempotencyKeyRepository;
import com.orderplatform.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final InventoryClient inventoryClient;
    private final RestTemplate restTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${services.product-service.url}")
    private String productServiceUrl;

    @Value("${spring.kafka.enabled:true}")
    private boolean kafkaEnabled;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            String key = request.getIdempotencyKey();
            idempotencyKeyRepository.findByKey(key).ifPresent(existing -> {
                log.info("Idempotent order creation for key {}: returning existing order {}",
                        key, existing.getOrderId());
                throw new IllegalStateException("Order already exists for idempotency key: " + key);
            });
        }

        // 2. Validate items are not empty
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        // 3. Pre-check inventory availability for ALL items (fail-fast)
        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            if (!inventoryClient.hasStock(itemReq.getProductId(), itemReq.getQuantity())) {
                throw new IllegalArgumentException(
                        "Insufficient inventory for product " + itemReq.getProductId()
                                + " (requested: " + itemReq.getQuantity() + ")");
            }
        }

        // 4. Build order
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
            BigDecimal price = fetchProductPrice(itemReq.getProductId());
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .productId(itemReq.getProductId())
                    .productName(itemReq.getProductName())
                    .quantity(itemReq.getQuantity())
                    .price(price)
                    .build();
            totalAmount = totalAmount.add(price.multiply(BigDecimal.valueOf(itemReq.getQuantity())));
            order.getItems().add(item);
        }

        order.setTotalAmount(totalAmount);
        order = orderRepository.save(order);

        // 5. Save idempotency key
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            idempotencyKeyRepository.save(new IdempotencyKey(
                    request.getIdempotencyKey(),
                    order.getId()
            ));
        }

        log.info("Order {} created for user {} with total {}",
                order.getOrderNumber(), order.getUserId(), order.getTotalAmount());

        sendKafkaEvent("order.created", order.getUserId(), Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "orderNumber", order.getOrderNumber(),
                "totalAmount", order.getTotalAmount()
        ));

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse reserveOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // Validate: only NEW -> RESERVED or CANCELLED
        OrderStatusTransitions.validateTransition(order.getStatus(), OrderStatus.RESERVED);

        // 1. Reserve ALL items atomically; any failure (reject or exception)
        //    triggers compensation of already reserved items
        List<OrderItem> successfullyReserved = new ArrayList<>();
        boolean reserveFailed = false;
        try {
            for (OrderItem item : order.getItems()) {
                if (!inventoryClient.reserve(item.getProductId(), item.getQuantity(), orderId)) {
                    reserveFailed = true;
                    break;
                }
                successfullyReserved.add(item);
            }
        } catch (RuntimeException e) {
            log.error("Order {} - inventory call failed while reserving: {}",
                    orderId, e.toString());
            reserveFailed = true;
        }

        // 2. Compensate on failure: release only what was actually reserved
        if (reserveFailed) {
            log.warn("Order {} - reserve failed ({}/{} items reserved), releasing {} reserved items",
                    orderId, successfullyReserved.size(), order.getItems().size(),
                    successfullyReserved.size());
            releaseReservations(orderId, successfullyReserved);
            order.setStatus(OrderStatus.CANCELLED);
        } else {
            order.setStatus(OrderStatus.RESERVED);
        }

        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        OrderStatus finalStatus = order.getStatus();
        log.info("Order {} -> {}", orderId, finalStatus);

        sendKafkaEvent("order.status-changed", order.getUserId(), Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "orderNumber", order.getOrderNumber(),
                "status", finalStatus.name()
        ));

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateStatus(String orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // Validate state transition
        OrderStatusTransitions.validateTransition(order.getStatus(), status);

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());

        if (status == OrderStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
        } else if (status == OrderStatus.SHIPPED) {
            order.setShippedAt(LocalDateTime.now());
        }

        order = orderRepository.save(order);
        log.info("Order {} status updated from {} to {}", orderId, previousStatus, status);

        // Compensation: cancelling a RESERVED order must release reserved stock
        if (status == OrderStatus.CANCELLED && previousStatus == OrderStatus.RESERVED) {
            releaseReservations(orderId, order.getItems());
        }

        sendKafkaEvent("order.status-changed", order.getUserId(), Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "orderNumber", order.getOrderNumber(),
                "status", status.name()
        ));

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

    /**
     * Best-effort компенсация резерва: сбой освобождения одной позиции
     * не должен прерывать освобождение остальных и основную операцию.
     * Ошибки логируются как сигнал для ручного разбора.
     */
    private void releaseReservations(String orderId, List<OrderItem> items) {
        for (OrderItem item : items) {
            try {
                inventoryClient.release(item.getProductId(), item.getQuantity(), orderId);
            } catch (RuntimeException e) {
                log.error("Order {} - failed to release {} x {} (manual cleanup may be required): {}",
                        orderId, item.getQuantity(), item.getProductId(), e.toString());
            }
        }
    }

    private BigDecimal fetchProductPrice(String productId) {
        try {
            String url = productServiceUrl + "/api/products/" + productId;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object price = response.getBody().get("price");
                if (price != null) {
                    return new BigDecimal(price.toString());
                }
            }
            log.warn("Product {} not found, using zero price", productId);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Failed to fetch price for product {}: {}", productId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private void sendKafkaEvent(String topic, String key, Object payload) {
        if (!kafkaEnabled) {
            log.debug("Kafka disabled, skipping event to topic {}", topic);
            return;
        }
        try {
            kafkaTemplate.send(topic, key, payload);
            log.debug("Sent event to topic {} for key {}", topic, key);
        } catch (Exception e) {
            log.error("Failed to send Kafka event to topic {}: {}", topic, e.getMessage());
        }
    }
}
