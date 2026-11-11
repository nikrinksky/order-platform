package com.orderplatform.inventory.service;

import com.orderplatform.inventory.dto.ReservationRequest;
import com.orderplatform.inventory.dto.ReservationResponse;
import com.orderplatform.inventory.model.InventoryItem;
import com.orderplatform.inventory.model.Reservation;
import com.orderplatform.inventory.model.ReservationStatus;
import com.orderplatform.inventory.repository.InventoryItemRepository;
import com.orderplatform.inventory.repository.ReservationRepository;
import com.orderplatform.outbox.OutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final String ORDER_ID = "order-1";
    private static final String PRODUCT_ID = "product-1";

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private InventoryService inventoryService;

    private InventoryItem sampleItem() {
        return InventoryItem.builder()
                .id("1")
                .productId(PRODUCT_ID)
                .quantity(20)
                .reserved(0)
                .available(true)
                .build();
    }

    private Reservation reservation(ReservationStatus status, int quantity) {
        return Reservation.builder()
                .id("res-1")
                .orderId(ORDER_ID)
                .productId(PRODUCT_ID)
                .quantity(quantity)
                .status(status)
                .build();
    }

    private ReservationRequest request(String orderId, String productId, int quantity) {
        return ReservationRequest.builder()
                .orderId(orderId)
                .productId(productId)
                .quantity(quantity)
                .build();
    }

    @Test
    void reserve_shouldReturnReservedWhenAvailable() {
        InventoryItem item = sampleItem();
        when(inventoryItemRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = inventoryService.reserve(request(ORDER_ID, PRODUCT_ID, 5));

        assertTrue(response.isReserved());
        assertEquals(ORDER_ID, response.getOrderId());
        assertEquals("Reserved successfully", response.getMessage());
        assertEquals(5, item.getReserved());

        // The reservation is a row of its own: that row is what a retry matches on.
        ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(reservationCaptor.capture());
        assertEquals(ORDER_ID, reservationCaptor.getValue().getOrderId());
        assertEquals(PRODUCT_ID, reservationCaptor.getValue().getProductId());
        assertEquals(5, reservationCaptor.getValue().getQuantity());
        assertEquals(ReservationStatus.ACTIVE, reservationCaptor.getValue().getStatus());

        // The event is appended inside the reservation transaction, not sent to Kafka directly
        verify(outboxService, times(1)).append(eq("inventory.reserved"), eq(ORDER_ID), anyString(),
                any(com.orderplatform.events.inventory.InventoryReservedEvent.class));
    }

    /**
     * The defect this guards: a retried reserve used to increment {@code reserved} again, so the
     * same order took the stock out of circulation twice.
     */
    @Test
    void reserve_shouldBeIdempotentForTheSameOrder() {
        InventoryItem item = sampleItem();
        item.setReserved(5);
        when(inventoryItemRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(item));
        when(reservationRepository.findByOrderIdAndProductId(ORDER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(reservation(ReservationStatus.ACTIVE, 5)));

        ReservationResponse response = inventoryService.reserve(request(ORDER_ID, PRODUCT_ID, 5));

        assertTrue(response.isReserved());
        assertEquals("Already reserved", response.getMessage());
        assertEquals(5, response.getQuantity());
        assertEquals(5, item.getReserved(), "the retry must not reserve the stock again");
        verify(inventoryItemRepository, never()).save(any(InventoryItem.class));
        verify(reservationRepository, never()).save(any(Reservation.class));
        verifyNoInteractions(outboxService);
    }

    /**
     * A reservation that was given back must not be resurrected silently: the order no longer needs
     * the stock, and reserving it again would hold goods for a cancelled order.
     */
    @Test
    void reserve_shouldRefuseWhenReservationWasReleased() {
        InventoryItem item = sampleItem();
        when(inventoryItemRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(item));
        when(reservationRepository.findByOrderIdAndProductId(ORDER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(reservation(ReservationStatus.RELEASED, 5)));

        ReservationResponse response = inventoryService.reserve(request(ORDER_ID, PRODUCT_ID, 5));

        assertFalse(response.isReserved());
        assertEquals("Reservation is already RELEASED", response.getMessage());
        assertEquals(0, item.getReserved());
        verifyNoInteractions(outboxService);
    }

    @Test
    void reserve_shouldRefuseWhenReservationWasCommitted() {
        InventoryItem item = sampleItem();
        when(inventoryItemRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(item));
        when(reservationRepository.findByOrderIdAndProductId(ORDER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(reservation(ReservationStatus.COMMITTED, 5)));

        ReservationResponse response = inventoryService.reserve(request(ORDER_ID, PRODUCT_ID, 5));

        assertFalse(response.isReserved());
        assertEquals("Reservation is already COMMITTED", response.getMessage());
        verifyNoInteractions(outboxService);
    }

    /**
     * The row must be read under a pessimistic lock. Without it two concurrent reservations read
     * the same free quantity and both pass, which is exactly the oversell defect.
     */
    @Test
    void reserve_shouldReadItemUnderLock() {
        when(inventoryItemRepository.findByProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(sampleItem()));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.reserve(request(ORDER_ID, PRODUCT_ID, 5));

        verify(inventoryItemRepository).findByProductIdForUpdate(PRODUCT_ID);
        verify(inventoryItemRepository, never()).findByProductId(PRODUCT_ID);
    }

    @Test
    void reserve_shouldRejectZeroQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.reserve(request(ORDER_ID, PRODUCT_ID, 0)));
        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void reserve_shouldRejectNegativeQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.reserve(request(ORDER_ID, PRODUCT_ID, -5)));
        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void reserve_shouldNotAppendEventWhenInsufficient() {
        InventoryItem item = sampleItem();
        when(inventoryItemRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(item));

        inventoryService.reserve(request(ORDER_ID, PRODUCT_ID, 30));

        verify(outboxService, never()).append(anyString(), anyString(), anyString(), any());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void reserve_shouldReturnNotReservedWhenInsufficient() {
        InventoryItem item = sampleItem();
        when(inventoryItemRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(item));

        ReservationResponse response = inventoryService.reserve(request(ORDER_ID, PRODUCT_ID, 30));

        assertFalse(response.isReserved());
        assertTrue(response.getMessage().contains("Insufficient inventory"));
        verify(inventoryItemRepository, never()).save(any(InventoryItem.class));
    }

    @Test
    void reserve_shouldCreateNewItemWhenNotFound() {
        when(inventoryItemRepository.findByProductIdForUpdate("new-product")).thenReturn(Optional.empty());
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = inventoryService.reserve(request(ORDER_ID, "new-product", 5));

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
        when(inventoryItemRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(sampleItem()));

        Optional<InventoryItem> result = inventoryService.findByProductId(PRODUCT_ID);

        assertTrue(result.isPresent());
        assertEquals(PRODUCT_ID, result.get().getProductId());
    }

    @Test
    void release_shouldDecreaseReservedQuantity() {
        InventoryItem item = sampleItem();
        item.setReserved(10);
        Reservation active = reservation(ReservationStatus.ACTIVE, 4);
        when(reservationRepository.findByOrderIdAndProductId(ORDER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(active));
        when(inventoryItemRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(item));
        when(reservationRepository.findByOrderIdAndProductIdForUpdate(ORDER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(active));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = inventoryService.release(ORDER_ID, PRODUCT_ID, 4);

        assertTrue(response.isReserved());
        assertEquals("Released successfully", response.getMessage());
        assertEquals(6, item.getReserved());
        assertEquals(ReservationStatus.RELEASED, active.getStatus());
    }

    /**
     * The defect this guards: a repeated release used to subtract the caller's quantity again,
     * which drained the reservation of whichever order happened to hold the stock.
     */
    @Test
    void release_shouldBeIdempotentForTheSameOrder() {
        InventoryItem item = sampleItem();
        item.setReserved(10);
        Reservation released = reservation(ReservationStatus.RELEASED, 4);
        when(reservationRepository.findByOrderIdAndProductId(ORDER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(released));
        when(inventoryItemRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(item));
        when(reservationRepository.findByOrderIdAndProductIdForUpdate(ORDER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(released));

        ReservationResponse response = inventoryService.release(ORDER_ID, PRODUCT_ID, 4);

        assertTrue(response.isReserved());
        assertEquals("Reservation is already RELEASED", response.getMessage());
        assertEquals(10, item.getReserved(), "the second release must not free the stock again");
        verify(inventoryItemRepository, never()).save(any(InventoryItem.class));
        verifyNoInteractions(outboxService);
    }

    /**
     * The caller's number is not the truth - the order's own reservation is. Releasing a different
     * amount would either leak stock or free someone else's hold.
     */
    @Test
    void release_shouldUseTheStoredQuantityNotTheCallerQuantity() {
        InventoryItem item = sampleItem();
        item.setReserved(10);
        Reservation active = reservation(ReservationStatus.ACTIVE, 5);
        when(reservationRepository.findByOrderIdAndProductId(ORDER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(active));
        when(inventoryItemRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(item));
        when(reservationRepository.findByOrderIdAndProductIdForUpdate(ORDER_ID, PRODUCT_ID))
                .thenReturn(Optional.of(active));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = inventoryService.release(ORDER_ID, PRODUCT_ID, 2);

        assertEquals(5, response.getQuantity(), "the held amount is what gets released");
        assertEquals(5, item.getReserved());
    }

    /** An order that never reserved anything must not touch the aggregate at all. */
    @Test
    void release_shouldDoNothingWhenTheOrderHoldsNoReservation() {
        ReservationResponse response = inventoryService.release(ORDER_ID, PRODUCT_ID, 5);

        assertTrue(response.isReserved());
        assertEquals("No reservation to release", response.getMessage());
        verifyNoInteractions(inventoryItemRepository);
        verifyNoInteractions(outboxService);
    }

    @Test
    void release_shouldRejectNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.release(ORDER_ID, PRODUCT_ID, 0));
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.release(ORDER_ID, PRODUCT_ID, -3));
        verifyNoInteractions(inventoryItemRepository);
    }

    /** A reservation without its inventory row is a broken state, not something to paper over. */
    @Test
    void release_shouldThrowWhenReservationExistsButItemIsMissing() {
        Reservation active = reservation(ReservationStatus.ACTIVE, 5);
        when(reservationRepository.findByOrderIdAndProductId(ORDER_ID, "nonexistent"))
                .thenReturn(Optional.of(active));
        when(inventoryItemRepository.findByProductIdForUpdate("nonexistent")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> inventoryService.release(ORDER_ID, "nonexistent", 5));
    }

    @Test
    void createOrUpdate_shouldCreateNewItem() {
        when(inventoryItemRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.empty());
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryItem item = inventoryService.createOrUpdate(PRODUCT_ID, 100);

        assertNotNull(item);
        assertEquals(PRODUCT_ID, item.getProductId());
        assertEquals(100, item.getQuantity());
        assertTrue(item.getAvailable());
    }

    @Test
    void createOrUpdate_shouldUpdateExistingItem() {
        InventoryItem item = sampleItem();
        when(inventoryItemRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryItem updated = inventoryService.createOrUpdate(PRODUCT_ID, 0);

        assertEquals(0, updated.getQuantity());
        assertFalse(updated.getAvailable());
    }

    @Test
    void createOrUpdate_shouldRejectNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> inventoryService.createOrUpdate(PRODUCT_ID, -1));
        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void updateQuantity_shouldUpdateExistingItem() {
        InventoryItem item = sampleItem();
        when(inventoryItemRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.updateQuantity(PRODUCT_ID, 150);

        assertEquals(150, item.getQuantity());
        assertTrue(item.getAvailable());
        verify(inventoryItemRepository, times(1)).save(item);
    }

    @Test
    void updateQuantity_shouldCreateNewItemWhenNotFound() {
        when(inventoryItemRepository.findByProductIdForUpdate("new-product")).thenReturn(Optional.empty());
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.updateQuantity("new-product", 42);

        ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryItemRepository, atLeastOnce()).save(captor.capture());
        InventoryItem created = captor.getValue();
        assertEquals("new-product", created.getProductId());
        assertEquals(42, created.getQuantity());
        assertTrue(created.getAvailable());
    }

    @Test
    void updateQuantity_shouldRejectNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> inventoryService.updateQuantity(PRODUCT_ID, -10));
        verifyNoInteractions(inventoryItemRepository);
    }
}
