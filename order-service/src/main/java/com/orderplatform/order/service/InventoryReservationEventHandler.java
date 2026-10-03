package com.orderplatform.order.service;

import com.orderplatform.events.order.OrderStatusChangedEvent;
import com.orderplatform.events.inventory.InventoryReservedEvent;
import com.orderplatform.order.model.ItemReservation;
import com.orderplatform.order.model.Order;
import com.orderplatform.order.model.OrderStatusTransitions;
import com.orderplatform.order.repository.ItemReservationRepository;
import com.orderplatform.order.repository.OrderRepository;
import com.orderplatform.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reconciles the order state with {@code inventory.reserved} events.
 *
 * <p>The REST call to inventory is the fast path, the event is the truth. The event is what
 * moves an order to {@code RESERVED} when every item has a reservation: a timed out REST call
 * that actually reserved the stock is corrected here, and a reservation the REST path already
 * knew about is recognised by the delivery id and skipped.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryReservationEventHandler {

    private static final String TOPIC_ORDER_STATUS_CHANGED = "order.status-changed";

    private final ItemReservationRepository reservationRepository;
    private final OrderRepository orderRepository;
    private final OutboxService outboxService;

    @Transactional
    public void onReserved(InventoryReservedEvent event, String eventId) {
        String orderId = text(event.getOrderId());
        String productId = text(event.getProductId());

        if (eventId != null && reservationRepository.findByEventId(eventId).isPresent()) {
            log.info("Reservation event {} already applied, dropping the redelivery", eventId);
            return;
        }
        reservationRepository.save(ItemReservation.builder()
                .eventId(eventId != null ? eventId : UUID.randomUUID().toString())
                .orderId(orderId)
                .productId(productId)
                .quantity(event.getQuantity())
                .createdAt(LocalDateTime.now())
                .build());

        orderRepository.findById(orderId).ifPresent(order -> tryCompleteReservation(order, orderId));
    }

    /**
     * Moves a {@code NEW} order to {@code RESERVED} once every item is covered by reservations.
     * Any other status means the fast path or an earlier event already did the work.
     */
    private void tryCompleteReservation(Order order, String orderId) {
        if (order.getStatus() != Order.OrderStatus.NEW) {
            log.debug("Order {} is {}, reservation event needs no transition", orderId, order.getStatus());
            return;
        }
        if (!allItemsReserved(order)) {
            log.debug("Order {} still has unreserved items, staying NEW", orderId);
            return;
        }
        OrderStatusTransitions.validateTransition(Order.OrderStatus.NEW, Order.OrderStatus.RESERVED);
        order.setStatus(Order.OrderStatus.RESERVED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("Order {} reserved (learned from inventory.reserved)", orderId);

        outboxService.append(TOPIC_ORDER_STATUS_CHANGED, orderId,
                orderId + ":STATUS:" + Order.OrderStatus.RESERVED.name(),
                OrderStatusChangedEvent.newBuilder()
                        .setOrderId(orderId)
                        .setUserId(order.getUserId())
                        .setOrderNumber(order.getOrderNumber())
                        .setStatus(Order.OrderStatus.RESERVED.name())
                        .build());
    }

    private boolean allItemsReserved(Order order) {
        return order.getItems().stream().allMatch(item ->
                reservationRepository.findByOrderIdAndProductId(order.getId(), item.getProductId())
                        .stream()
                        .mapToInt(ItemReservation::getQuantity)
                        .sum() >= item.getQuantity());
    }

    private static String text(CharSequence value) {
        return value == null ? null : value.toString();
    }
}
