package com.orderplatform.order.service;

import com.orderplatform.order.client.InventoryClient;
import com.orderplatform.order.dto.CreateOrderRequest;
import com.orderplatform.order.dto.OrderResponse;
import com.orderplatform.order.model.IdempotencyKey;
import com.orderplatform.order.model.Order;
import com.orderplatform.order.model.OrderItem;
import com.orderplatform.order.repository.IdempotencyKeyRepository;
import com.orderplatform.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "productServiceUrl", "http://product-service:8089");
        ReflectionTestUtils.setField(orderService, "kafkaEnabled", false);
    }

    private Order sampleOrder() {
        return Order.builder()
                .id("order-1")
                .userId("user-1")
                .orderNumber("ORD-ABC12345")
                .status(Order.OrderStatus.NEW)
                .totalAmount(BigDecimal.ZERO)
                .items(List.of(OrderItem.builder()
                        .productId("product-1")
                        .productName("Test")
                        .quantity(2)
                        .price(BigDecimal.ZERO)
                        .build()))
                .build();
    }

    private Order twoItemOrder() {
        return Order.builder()
                .id("order-1")
                .userId("user-1")
                .orderNumber("ORD-ABC12345")
                .status(Order.OrderStatus.NEW)
                .totalAmount(BigDecimal.ZERO)
                .items(List.of(
                        OrderItem.builder()
                                .productId("product-1")
                                .productName("Item1")
                                .quantity(2)
                                .price(BigDecimal.ZERO)
                                .build(),
                        OrderItem.builder()
                                .productId("product-2")
                                .productName("Item2")
                                .quantity(3)
                                .price(BigDecimal.ZERO)
                                .build()
                ))
                .build();
    }

    private CreateOrderRequest sampleRequest() {
        return CreateOrderRequest.builder()
                .userId("user-1")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("product-1")
                                .productName("Test Product")
                                .quantity(2)
                                .build()
                ))
                .build();
    }

    @Test
    void createOrder_shouldReturnCreatedOrder() {
        when(inventoryClient.hasStock(eq("product-1"), eq(2))).thenReturn(true);
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("price", "10.00")));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.createOrder(sampleRequest());

        assertNotNull(response);
        assertEquals("user-1", response.getUserId());
        assertEquals("NEW", response.getStatus());
        assertEquals(new BigDecimal("20.00"), response.getTotalAmount());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();
        assertEquals(new BigDecimal("20.00"), saved.getTotalAmount());
        assertEquals(new BigDecimal("10.00"), saved.getItems().get(0).getPrice());
    }

    @Test
    void createOrder_shouldRejectWhenStockInsufficient() {
        when(inventoryClient.hasStock(anyString(), anyInt())).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(sampleRequest()));

        assertTrue(ex.getMessage().contains("Insufficient inventory"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_shouldRejectEmptyItems() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId("user-1")
                .items(List.of())
                .build();

        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(request));
    }

    @Test
    void createOrder_shouldReturnConflictForDuplicateIdempotencyKey() {
        CreateOrderRequest request = sampleRequest();
        request.setIdempotencyKey("key-1");
        when(idempotencyKeyRepository.findByKey("key-1"))
                .thenReturn(Optional.of(new IdempotencyKey("key-1", "order-existing")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(request));

        assertTrue(ex.getMessage().contains("key-1"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_shouldPersistIdempotencyKey() {
        CreateOrderRequest request = sampleRequest();
        request.setIdempotencyKey("key-1");
        when(idempotencyKeyRepository.findByKey("key-1")).thenReturn(Optional.empty());
        when(inventoryClient.hasStock(anyString(), anyInt())).thenReturn(true);
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("price", "10.00")));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.createOrder(request);

        ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotencyKeyRepository).save(captor.capture());
        assertEquals("key-1", captor.getValue().getKey());
    }

    @Test
    void findById_shouldReturnOrder() {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(sampleOrder()));

        OrderResponse response = orderService.findById("order-1");

        assertNotNull(response);
        assertEquals("order-1", response.getId());
    }

    @Test
    void findById_shouldThrowExceptionWhenNotFound() {
        when(orderRepository.findById("999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> orderService.findById("999"));
    }

    @Test
    void findByUserId_shouldReturnOrders() {
        when(orderRepository.findByUserId("user-1")).thenReturn(List.of(sampleOrder()));

        List<OrderResponse> result = orderService.findByUserId("user-1");

        assertEquals(1, result.size());
        assertEquals("user-1", result.get(0).getUserId());
    }

    @Test
    void reserveOrder_shouldReserveWhenInventoryAvailable() {
        Order order = sampleOrder();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(inventoryClient.reserve(eq("product-1"), eq(2), eq("order-1"))).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.reserveOrder("order-1");

        assertEquals("RESERVED", response.getStatus());
        verify(inventoryClient, never()).release(anyString(), anyInt(), anyString());
    }

    @Test
    void reserveOrder_shouldCancelWhenInventoryUnavailable() {
        Order order = sampleOrder();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(inventoryClient.reserve(anyString(), anyInt(), anyString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.reserveOrder("order-1");

        assertEquals("CANCELLED", response.getStatus());
    }

    @Test
    void reserveOrder_shouldThrowWhenOrderNotFound() {
        when(orderRepository.findById("999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> orderService.reserveOrder("999"));
    }

    @Test
    void reserveOrder_shouldThrowWhenNotNewStatus() {
        Order order = sampleOrder();
        order.setStatus(Order.OrderStatus.RESERVED);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class, () -> orderService.reserveOrder("order-1"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void reserveOrder_shouldReleaseReservedItemsOnPartialFailure() {
        Order order = twoItemOrder();

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(inventoryClient.reserve(eq("product-1"), anyInt(), anyString())).thenReturn(true);
        when(inventoryClient.reserve(eq("product-2"), anyInt(), anyString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.reserveOrder("order-1");

        assertEquals("CANCELLED", response.getStatus());
        verify(inventoryClient, times(1)).release("product-1", 2, "order-1");
        verify(inventoryClient, never()).release(eq("product-2"), anyInt(), anyString());
    }

    @Test
    void reserveOrder_shouldCompensateEveryReservedItem() {
        Order order = Order.builder()
                .id("order-1")
                .userId("user-1")
                .orderNumber("ORD-ABC12345")
                .status(Order.OrderStatus.NEW)
                .totalAmount(BigDecimal.ZERO)
                .items(List.of(
                        OrderItem.builder().productId("product-1").productName("Item1")
                                .quantity(1).price(BigDecimal.ZERO).build(),
                        OrderItem.builder().productId("product-2").productName("Item2")
                                .quantity(2).price(BigDecimal.ZERO).build(),
                        OrderItem.builder().productId("product-3").productName("Item3")
                                .quantity(3).price(BigDecimal.ZERO).build()
                ))
                .build();

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(inventoryClient.reserve(eq("product-1"), anyInt(), anyString())).thenReturn(true);
        when(inventoryClient.reserve(eq("product-2"), anyInt(), anyString())).thenReturn(true);
        when(inventoryClient.reserve(eq("product-3"), anyInt(), anyString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.reserveOrder("order-1");

        assertEquals("CANCELLED", response.getStatus());
        verify(inventoryClient).release("product-1", 1, "order-1");
        verify(inventoryClient).release("product-2", 2, "order-1");
        verify(inventoryClient, never()).release(eq("product-3"), anyInt(), anyString());
    }

    @Test
    void reserveOrder_shouldCancelWhenCompensationFails() {
        Order order = twoItemOrder();

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(inventoryClient.reserve(eq("product-1"), anyInt(), anyString())).thenReturn(true);
        when(inventoryClient.reserve(eq("product-2"), anyInt(), anyString())).thenReturn(false);
        doThrow(new IllegalStateException("inventory-service unavailable"))
                .when(inventoryClient).release(anyString(), anyInt(), anyString());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.reserveOrder("order-1");

        assertEquals("CANCELLED", response.getStatus());
        verify(inventoryClient).release("product-1", 2, "order-1");
    }

    @Test
    void reserveOrder_shouldCompensateWhenReserveThrows() {
        Order order = twoItemOrder();

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(inventoryClient.reserve(eq("product-1"), anyInt(), anyString())).thenReturn(true);
        when(inventoryClient.reserve(eq("product-2"), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("inventory-service unavailable"));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.reserveOrder("order-1");

        assertEquals("CANCELLED", response.getStatus());
        verify(inventoryClient).release("product-1", 2, "order-1");
        verify(inventoryClient, never()).release(eq("product-2"), anyInt(), anyString());
    }

    @Test
    void updateStatus_shouldReleaseReservationsWhenReservedOrderCancelled() {
        Order order = sampleOrder();
        order.setStatus(Order.OrderStatus.RESERVED);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus("order-1", Order.OrderStatus.CANCELLED);

        assertEquals("CANCELLED", response.getStatus());
        verify(inventoryClient).release("product-1", 2, "order-1");
    }

    @Test
    void updateStatus_shouldNotReleaseWhenNewOrderCancelled() {
        Order order = sampleOrder();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus("order-1", Order.OrderStatus.CANCELLED);

        assertEquals("CANCELLED", response.getStatus());
        verify(inventoryClient, never()).release(anyString(), anyInt(), anyString());
    }

    @Test
    void updateStatus_shouldUpdateToPaid() {
        Order order = sampleOrder();
        order.setStatus(Order.OrderStatus.RESERVED);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus("order-1", Order.OrderStatus.PAID);

        assertEquals("PAID", response.getStatus());
        assertNotNull(order.getPaidAt());
    }

    @Test
    void updateStatus_shouldUpdateToShipped() {
        Order order = sampleOrder();
        order.setStatus(Order.OrderStatus.PAID);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus("order-1", Order.OrderStatus.SHIPPED);

        assertEquals("SHIPPED", response.getStatus());
        assertNotNull(order.getShippedAt());
    }

    @Test
    void updateStatus_shouldRejectSkippedState() {
        Order order = sampleOrder();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.updateStatus("order-1", Order.OrderStatus.SHIPPED));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void updateStatus_shouldRejectTransitionFromTerminalState() {
        Order order = sampleOrder();
        order.setStatus(Order.OrderStatus.COMPLETED);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.updateStatus("order-1", Order.OrderStatus.NEW));
    }

    @Test
    void updateStatus_shouldThrowWhenNotFound() {
        when(orderRepository.findById("999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> orderService.updateStatus("999", Order.OrderStatus.PAID));
    }
}
