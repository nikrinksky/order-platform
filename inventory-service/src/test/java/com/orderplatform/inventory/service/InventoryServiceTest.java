package com.orderplatform.inventory.service;

import com.orderplatform.inventory.dto.ReservationRequest;
import com.orderplatform.inventory.dto.ReservationResponse;
import com.orderplatform.inventory.model.InventoryItem;
import com.orderplatform.inventory.repository.InventoryItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void reserve_shouldReturnReservedWhenAvailable() {
        ReservationRequest request = ReservationRequest.builder()
                .orderId("order-1")
                .productId("product-1")
                .quantity(5)
                .build();

        InventoryItem item = InventoryItem.builder()
                .id("1")
                .productId("product-1")
                .quantity(20)
                .reserved(0)
                .available(true)
                .build();

        when(inventoryItemRepository.findByProductId("product-1")).thenReturn(Optional.of(item));

        ReservationResponse response = inventoryService.reserve(request);

        assertTrue(response.isReserved());
        assertEquals("order-1", response.getOrderId());
        verify(inventoryItemRepository, times(1)).save(any(InventoryItem.class));
    }

    @Test
    void reserve_shouldReturnNotReservedWhenInsufficient() {
        ReservationRequest request = ReservationRequest.builder()
                .orderId("order-1")
                .productId("product-1")
                .quantity(30)
                .build();

        InventoryItem item = InventoryItem.builder()
                .id("1")
                .productId("product-1")
                .quantity(20)
                .reserved(0)
                .available(true)
                .build();

        when(inventoryItemRepository.findByProductId("product-1")).thenReturn(Optional.of(item));

        ReservationResponse response = inventoryService.reserve(request);

        assertFalse(response.isReserved());
        assertTrue(response.getMessage().contains("Insufficient inventory"));
    }

    @Test
    void findByProductId_shouldReturnItem() {
        InventoryItem item = InventoryItem.builder()
                .id("1")
                .productId("product-1")
                .quantity(20)
                .reserved(0)
                .available(true)
                .build();

        when(inventoryItemRepository.findByProductId("product-1")).thenReturn(Optional.of(item));

        Optional<InventoryItem> result = inventoryService.findByProductId("product-1");

        assertTrue(result.isPresent());
        assertEquals("product-1", result.get().getProductId());
    }
}
