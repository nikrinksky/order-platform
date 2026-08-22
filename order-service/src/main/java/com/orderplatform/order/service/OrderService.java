package com.orderplatform.order.service;

import com.orderplatform.order.dto.CreateOrderRequest;
import com.orderplatform.order.dto.OrderResponse;
import com.orderplatform.order.model.Order;
import com.orderplatform.order.model.OrderItem;
import com.orderplatform.order.model.Order.OrderStatus;
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
    private final RestTemplate restTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${services.product-service.url}")
    private String productServiceUrl;

    @Value("${services.inventory-service.url}")
    private String inventoryServiceUrl;

    @Value("${spring.kafka.enabled:true}")
    private boolean kafkaEnabled;

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
        log.info("Order {} created for user {} with total {}", order.getOrderNumber(),
                order.getUserId(), order.getTotalAmount());

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

        if (order.getStatus() != OrderStatus.NEW) {
            throw new RuntimeException("Order can only be reserved in NEW status");
        }

        List<OrderItem> successfullyReserved = new ArrayList<>();
        boolean allReserved = true;

        for (OrderItem item : order.getItems()) {
            boolean reserved = reserveInventory(item.getProductId(), item.getQuantity(), orderId);
            if (reserved) {
                successfullyReserved.add(item);
            } else {
                allReserved = false;
                break;
            }
        }

        if (!allReserved) {
            log.warn("Order {} - partial reserve failed, releasing {} items", orderId,
                    successfullyReserved.size());
            for (OrderItem item : successfullyReserved) {
                releaseInventory(item.getProductId(), item.getQuantity(), orderId);
            }
            order.setStatus(OrderStatus.CANCELLED);
        } else {
            order.setStatus(OrderStatus.RESERVED);
        }

        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        OrderStatus finalStatus = order.getStatus();
        log.info("Order {} {}", orderId, finalStatus);

        sendKafkaEvent("order.status-changed", order.getUserId(), Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "orderNumber", order.getOrderNumber(),
                "status", finalStatus.name()
        ));

        return OrderResponse.from(order);
    }

    private boolean reserveInventory(String productId, Integer quantity, String orderId) {
        try {
            String url = inventoryServiceUrl + "/api/inventory/reserve";
            Map<String, Object> request = Map.of(
                    "orderId", orderId,
                    "productId", productId,
                    "quantity", quantity
            );
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return false;
            }
            Object reservedFlag = response.getBody().get("reserved");
            return Boolean.TRUE.equals(reservedFlag);
        } catch (Exception e) {
            log.error("Failed to reserve inventory for product {}: {}", productId, e.getMessage());
            return false;
        }
    }

    private void releaseInventory(String productId, Integer quantity, String orderId) {
        try {
            String url = inventoryServiceUrl + "/api/inventory/release?orderId=" + orderId
                    + "&productId=" + productId + "&quantity=" + quantity;
            restTemplate.postForEntity(url, null, Map.class);
            log.info("Released {} units of product {} for order {}", quantity, productId, orderId);
        } catch (Exception e) {
            log.error("Failed to release inventory for product {}: {}", productId, e.getMessage());
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

    @Transactional
    public OrderResponse updateStatus(String orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());

        if (status == OrderStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
        } else if (status == OrderStatus.SHIPPED) {
            order.setShippedAt(LocalDateTime.now());
        }

        order = orderRepository.save(order);
        log.info("Order {} status updated from {} to {}", orderId, oldStatus, status);

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
