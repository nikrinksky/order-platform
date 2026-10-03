package com.orderplatform.order.listener;

import com.orderplatform.events.EventHeaders;
import com.orderplatform.events.inventory.InventoryReservedEvent;
import com.orderplatform.order.service.InventoryReservationEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code inventory.reserved} and reconciles the order state.
 *
 * <p>Manual ack: the offset moves only after the event is stored, so a crash between receive
 * and store replays the event instead of losing the reservation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryReservedListener {

    private final InventoryReservationEventHandler handler;

    @KafkaListener(topics = "inventory.reserved", groupId = "order-service-group")
    public void onInventoryReserved(InventoryReservedEvent event,
                                    @Header(name = EventHeaders.EVENT_ID, required = false) String eventId,
                                    Acknowledgment acknowledgment) {
        log.info("Received inventory.reserved event for order {}", event.getOrderId());
        handler.onReserved(event, eventId);
        acknowledgment.acknowledge();
    }
}
