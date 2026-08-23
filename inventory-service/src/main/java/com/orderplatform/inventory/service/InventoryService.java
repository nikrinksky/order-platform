package com.orderplatform.inventory.service;

import com.orderplatform.inventory.dto.ReservationRequest;
import com.orderplatform.inventory.dto.ReservationResponse;
import com.orderplatform.inventory.model.InventoryItem;
import com.orderplatform.inventory.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.enabled:true}")
    private boolean kafkaEnabled;

    public Optional<InventoryItem> findByProductId(String productId) {
        return inventoryItemRepository.findByProductId(productId);
    }

    @Transactional
    public ReservationResponse reserve(ReservationRequest request) {
        InventoryItem item = inventoryItemRepository.findByProductId(request.getProductId())
                .orElseGet(() -> {
                    InventoryItem newItem = InventoryItem.builder()
                            .id(java.util.UUID.randomUUID().toString())
                            .productId(request.getProductId())
                            .quantity(0)
                            .reserved(0)
                            .available(false)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return inventoryItemRepository.save(newItem);
                });

        int availableQuantity = item.getQuantity() - item.getReserved();
        if (availableQuantity >= request.getQuantity()) {
            item.setReserved(item.getReserved() + request.getQuantity());
            item.setUpdatedAt(LocalDateTime.now());
            inventoryItemRepository.save(item);

            log.info("Reserved {} units of product {} for order {}", request.getQuantity(), request.getProductId(), request.getOrderId());
            sendKafkaEvent("inventory.reserved", request.getOrderId(), Map.of(
                    "orderId", request.getOrderId(),
                    "productId", request.getProductId(),
                    "quantity", request.getQuantity(),
                    "available", availableQuantity - request.getQuantity()
            ));
            return ReservationResponse.builder()
                    .orderId(request.getOrderId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .reserved(true)
                    .message("Reserved successfully")
                    .build();
        } else {
            log.warn("Insufficient inventory for product {}. Available: {}, Requested: {}",
                    request.getProductId(), availableQuantity, request.getQuantity());
            return ReservationResponse.builder()
                    .orderId(request.getOrderId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .reserved(false)
                    .message("Insufficient inventory. Available: " + availableQuantity)
                    .build();
        }
    }

    @Transactional
    public ReservationResponse release(String orderId, String productId, Integer quantity) {
        InventoryItem item = inventoryItemRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory item not found for product: " + productId));

        item.setReserved(Math.max(0, item.getReserved() - quantity));
        item.setUpdatedAt(LocalDateTime.now());
        inventoryItemRepository.save(item);

        log.info("Released {} units of product {} for order {}", quantity, productId, orderId);
        sendKafkaEvent("inventory.released", orderId, Map.of(
                "orderId", orderId,
                "productId", productId,
                "quantity", quantity
        ));
        return ReservationResponse.builder()
                .orderId(orderId)
                .productId(productId)
                .quantity(quantity)
                .reserved(true)
                .message("Released successfully")
                .build();
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

    @Transactional
    public InventoryItem createOrUpdate(String productId, Integer quantity) {
        InventoryItem item = inventoryItemRepository.findByProductId(productId)
                .orElseGet(() -> {
                    InventoryItem newItem = InventoryItem.builder()
                            .id(java.util.UUID.randomUUID().toString())
                            .productId(productId)
                            .quantity(0)
                            .reserved(0)
                            .available(false)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return inventoryItemRepository.save(newItem);
                });

        item.setQuantity(quantity);
        item.setAvailable(quantity > 0);
        item.setUpdatedAt(LocalDateTime.now());
        inventoryItemRepository.save(item);
        
        log.info("Created/Updated inventory for product {}: quantity={}", productId, quantity);
        return item;
    }

    @Transactional
    public void updateQuantity(String productId, Integer quantity) {
        InventoryItem item = inventoryItemRepository.findByProductId(productId)
                .orElseGet(() -> {
                    InventoryItem newItem = InventoryItem.builder()
                            .id(java.util.UUID.randomUUID().toString())
                            .productId(productId)
                            .quantity(quantity)
                            .reserved(0)
                            .available(quantity > 0)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return inventoryItemRepository.save(newItem);
                });

        item.setQuantity(quantity);
        item.setAvailable(quantity > 0);
        item.setUpdatedAt(LocalDateTime.now());
        inventoryItemRepository.save(item);
    }
}
