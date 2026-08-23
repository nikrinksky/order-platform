package com.orderplatform.inventory.service;

import com.orderplatform.inventory.dto.ReservationRequest;
import com.orderplatform.inventory.dto.ReservationResponse;
import com.orderplatform.inventory.model.InventoryItem;
import com.orderplatform.inventory.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inventoryService, "kafkaEnabled", false);
    }

    private InventoryItem sampleItem() {
        return InventoryItem.builder()
                .id("1")
                .productId("product-1")
                .quantity(20)
                .reserved(0)
                .available(true)
                .build();
    }

    @Test
    void reserve_shouldReturnReservedWhenAvailable() {
        ReservationRequest request = ReservationRequest.builder()
                .orderId("order-1")
                .productId("product-1")
                .quantity(5)
                .build();

        InventoryItem item = sampleItem();
        when(inventoryItemRepository.findByProductId("product-1")).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = inventoryService.reserve(request);

        assertTrue(response.isReserved());
        assertEquals("order-1", response.getOrderId());
        assertEquals("Reserved successfully", response.getMessage());
        verify(inventoryItemRepository, times(1)).save(any(InventoryItem.class));
        assertEquals(5, item.getReserved());
    }

    @Test
    void reserve_shouldReturnNotReservedWhenInsufficient() {
        ReservationRequest request = ReservationRequest.builder()
                .orderId("order-1")
                .productId("product-1")
                .quantity(30)
                .build();

        InventoryItem item = sampleItem();
        when(inventoryItemRepository.findByProductId("product-1")).thenReturn(Optional.of(item));

        ReservationResponse response = inventoryService.reserve(request);

        assertFalse(response.isReserved());
        assertTrue(response.getMessage().contains("Insufficient inventory"));
        verify(inventoryItemRepository, never()).save(any(InventoryItem.class));
    }

    @Test
    void reserve_shouldCreateNewItemWhenNotFound() {
        ReservationRequest request = ReservationRequest.builder()
                .orderId("order-1")
                .productId("new-product")
                .quantity(5)
                .build();

        when(inventoryItemRepository.findByProductId("new-product")).thenReturn(Optional.empty());
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = inventoryService.reserve(request);

        assertFalse(response.isReserved());
        verify(inventoryItemRepository, atLeastOnce()).save(any(InventoryItem.class));

        ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryItemRepository, atLeastOnce()).save(captor.capture());
        assertNotNull(captor.getValue().getId());
        assertEquals("new-product", captor.getValue().getProductId());
        assertEquals(0, captor.getValue().getQuantity());
        assertEquals(0, captor.getValue().getReserved());
    }

    @Test
    void findByProductId_shouldReturnItem() {
        when(inventoryItemRepository.findByProductId("product-1")).thenReturn(Optional.of(sampleItem()));

        Optional<InventoryItem> result = inventoryService.findByProductId("product-1");

        assertTrue(result.isPresent());
        assertEquals("product-1", result.get().getProductId());
    }

    @Test
    void release_shouldDecreaseReservedQuantity() {
        InventoryItem item = sampleItem();
        item.setReserved(10);
        when(inventoryItemRepository.findByProductId("product-1")).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = inventoryService.release("order-1", "product-1", 4);

        assertTrue(response.isReserved());
        assertEquals("Released successfully", response.getMessage());
        assertEquals(6, item.getReserved());
    }

    @Test
    void release_shouldNotGoBelowZero() {
        InventoryItem item = sampleItem();
        item.setReserved(2);
        when(inventoryItemRepository.findByProductId("product-1")).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.release("order-1", "product-1", 10);

        assertEquals(0, item.getReserved());
    }

    @Test
    void release_shouldThrowWhenItemNotFound() {
        when(inventoryItemRepository.findByProductId("nonexistent")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> inventoryService.release("order-1", "nonexistent", 5));
    }

    @Test
    void createOrUpdate_shouldCreateNewItem() {
        when(inventoryItemRepository.findByProductId("product-1")).thenReturn(Optional.empty());
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryItem item = inventoryService.createOrUpdate("product-1", 100);

        assertNotNull(item);
        assertEquals("product-1", item.getProductId());
        assertEquals(100, item.getQuantity());
        assertTrue(item.getAvailable());
    }

    @Test
    void createOrUpdate_shouldUpdateExistingItem() {
        InventoryItem item = sampleItem();
        when(inventoryItemRepository.findByProductId("product-1")).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryItem updated = inventoryService.createOrUpdate("product-1", 0);

        assertEquals(0, updated.getQuantity());
        assertFalse(updated.getAvailable());
    }

    @Test
    void updateQuantity_shouldUpdateExistingItem() {
        InventoryItem item = sampleItem();
        when(inventoryItemRepository.findByProductId("product-1")).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.updateQuantity("product-1", 150);

        assertEquals(150, item.getQuantity());
        assertTrue(item.getAvailable());
        verify(inventoryItemRepository, times(1)).save(item);
    }

    @Test
    void updateQuantity_shouldCreateNewItemWhenNotFound() {
        when(inventoryItemRepository.findByProductId("new-product")).thenReturn(Optional.empty());
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.updateQuantity("new-product", 42);

        ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryItemRepository, atLeastOnce()).save(captor.capture());
        InventoryItem created = captor.getValue();
        assertEquals("new-product", created.getProductId());
        assertEquals(42, created.getQuantity());
        assertTrue(created.getAvailable());
    }
}
