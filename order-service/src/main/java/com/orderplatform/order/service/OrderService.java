package com.orderplatform.order.service;

import com.orderplatform.events.order.OrderCreatedEvent;
import com.orderplatform.events.order.OrderStatusChangedEvent;
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
import com.orderplatform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
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
    private final OutboxService outboxService;

    @Value("${services.product-service.url}")
    private String productServiceUrl;

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

        // 3. Build order
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

        // 4. Hold the stock right away. Ordering used to need a second, administrator-only call
        //    before the order could leave NEW, so a normal customer order never reached RESERVED
        //    on its own. The hold is compensated if any item is rejected, and the exception rolls
        //    the transaction back, so a failed reservation leaves no half-created order behind.
        reserveItems(order);
        order.setStatus(OrderStatus.RESERVED);
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        // 5. Save idempotency key
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            idempotencyKeyRepository.save(new IdempotencyKey(
                    request.getIdempotencyKey(),
                    order.getId()
            ));
        }

        log.info("Order {} created for user {} with total {}, stock reserved",
                order.getOrderNumber(), order.getUserId(), order.getTotalAmount());

        outboxService.append(Topics.ORDER_CREATED, order.getId(),
                order.getId() + ":CREATED",
                OrderCreatedEvent.newBuilder()
                        .setOrderId(order.getId())
                        .setUserId(order.getUserId())
                        .setOrderNumber(order.getOrderNumber())
                        .setTotalAmount(toAvroDecimal(order.getTotalAmount()))
                        .build());

        return OrderResponse.from(order);
    }

    /**
     * Reserves every item of a newly created order.
     *
     * <p>All or nothing: if an item is rejected, the ones already reserved are given back before
     * the exception propagates. Without that compensation the earlier items would stay held for
     * an order that is about to be rolled back.
     *
     * @param order the order whose items are reserved
     * @throws IllegalArgumentException when an item cannot be reserved
     */
    private void reserveItems(Order order) {
        List<OrderItem> reserved = new ArrayList<>();
        try {
            for (OrderItem item : order.getItems()) {
                if (!inventoryClient.reserve(item.getProductId(), item.getQuantity(), order.getId())) {
                    releaseReservations(order.getId(), reserved);
                    throw new IllegalArgumentException(
                            "Insufficient inventory for product " + item.getProductId() +
                                    " (requested: " + item.getQuantity() + ")");
                }
                reserved.add(item);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Order {} - inventory call failed while reserving: {}", order.getId(), e.toString());
            releaseReservations(order.getId(), reserved);
            throw new IllegalStateException(
                    "Could not reserve stock for order " + order.getId(), e);
        }
    }

    /**
     * Holds the stock of an existing {@code NEW} order.
     *
     * <p>New orders are reserved on creation, so this is no longer part of the normal flow. It
     * stays as the manual path for orders created before that change, and for a retry by an
     * administrator after the stock was restocked.</p>
     */
    @Transactional
    public OrderResponse reserveOrder(String orderId) {
        // Locked read: a concurrent or repeated reserve must not reserve the same stock twice
        Order order = orderRepository.findByIdForUpdate(orderId)
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

        appendStatusChanged(order, finalStatus);

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateStatus(String orderId, OrderStatus status) {
        // Locked read: a status change racing with a cancellation must not both be applied
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // Validate state transition
        OrderStatusTransitions.validateTransition(order.getStatus(), status);

        // The goods left the warehouse, so the stock has to be written off before the order is
        // declared complete. A failure aborts the transition: marking an order COMPLETED while the
        // reservation stays behind would keep the goods out of circulation for good.
        if (status == OrderStatus.COMPLETED && !inventoryClient.commit(orderId)) {
            throw new IllegalStateException(
                    "Cannot complete order " + orderId + ": inventory-service did not confirm the stock write-off");
        }

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

        // Compensation: cancelling an order that still holds a reservation must release the stock.
        // The reservation is held from RESERVED until the goods leave the warehouse (SHIPPED), so
        // a cancellation from PAID leaks stock unless it is released here too. SHIPPED is not in
        // this list: such an order can no longer be cancelled, and its stock is written off on
        // COMPLETED instead.
        if (status == OrderStatus.CANCELLED && holdsReservation(previousStatus)) {
            releaseReservations(orderId, order.getItems());
        }

        appendStatusChanged(order, status);

        return OrderResponse.from(order);
    }

    /** Whether an order in this status still holds stock reserved in inventory. */
    private static boolean holdsReservation(OrderStatus status) {
        return status == OrderStatus.RESERVED ||
                status == OrderStatus.PAID;
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

    /**
     * Reads the price of a product from the catalogue.
     *
     * <p>An order is a money document, so an unknown or unpriced product is rejected instead of
     * being silently priced at zero: the old fallback created orders for products that do not
     * exist and made them look free.
     *
     * @param productId product id
     * @return the catalogue price
     * @throws IllegalArgumentException if the product is unknown or has no price
     */
    private BigDecimal fetchProductPrice(String productId) {
        String url = productServiceUrl + "/api/products/" + productId;
        Map<?, ?> product;
        try {
            product = restTemplate.getForObject(url, Map.class);
        } catch (HttpStatusCodeException e) {
            // 404, or a hidden product that the catalogue refuses to show
            throw new IllegalArgumentException("Unknown product: " + productId, e);
        }
        Object price = product == null ? null : product.get("price");
        if (price == null) {
            throw new IllegalArgumentException("Product " + productId + " has no price");
        }
        return new BigDecimal(price.toString());
    }

    /**
     * Appends the status change event inside the caller's transaction. The event id names the
     * target status, so a repeated transition of the same order to the same status cannot
     * produce two events.
     */
    private void appendStatusChanged(Order order, OrderStatus status) {
        outboxService.append(Topics.ORDER_STATUS_CHANGED, order.getId(),
                order.getId() + ":STATUS:" + status.name(),
                OrderStatusChangedEvent.newBuilder()
                        .setOrderId(order.getId())
                        .setUserId(order.getUserId())
                        .setOrderNumber(order.getOrderNumber())
                        .setStatus(status.name())
                        .build());
    }

    /**
     * Encodes a money amount the way the Avro {@code bytes + decimal(19,2)} contract expects it:
     * the unscaled value as big-endian two's complement, exactly what Avro's own
     * {@code Conversions.DecimalConversion} produces.
     */
    private static ByteBuffer toAvroDecimal(BigDecimal value) {
        byte[] unscaled = value.setScale(2, RoundingMode.HALF_UP).unscaledValue().toByteArray();
        return ByteBuffer.wrap(unscaled);
    }

    /** Kafka topics this service publishes to. Kept here so no string is typed twice. */
    static final class Topics {
        static final String ORDER_CREATED = "order.created";
        static final String ORDER_STATUS_CHANGED = "order.status-changed";

        private Topics() {
        }
    }
}
