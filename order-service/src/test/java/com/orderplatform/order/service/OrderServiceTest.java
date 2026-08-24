package com.orderplatform.order.service;

import com.orderplatform.order.dto.CreateOrderRequest;
import com.orderplatform.order.dto.OrderResponse;
import com.orderplatform.order.model.Order;
import com.orderplatform.order.model.OrderItem;
import com.orderplatform.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderService orderService;

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

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "productServiceUrl", "http://product-service:8089");
        ReflectionTestUtils.setField(orderService, "inventoryServiceUrl", "http://inventory-service:8083");
        ReflectionTestUtils.setField(orderService, "kafkaEnabled", false);
    }

    @Test
    void createOrder_shouldReturnCreatedOrder() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId("user-1")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("product-1")
                                .productName("Test Product")
                                .quantity(2)
                                .build()
                ))
                .build();

        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("price", "10.00")));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response);
        assertEquals("user-1", response.getUserId());
        assertEquals("NEW", response.getStatus());
        assertEquals(new BigDecimal("20.00"), response.getTotalAmount());
        verify(orderRepository, times(1)).save(any(Order.class));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();
        assertEquals(new BigDecimal("20.00"), saved.getTotalAmount());
        assertEquals(new BigDecimal("10.00"), saved.getItems().get(0).getPrice());
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
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("reserved", true)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.reserveOrder("order-1");

        assertEquals("RESERVED", response.getStatus());
    }

    @Test
    void reserveOrder_shouldCancelWhenInventoryUnavailable() {
        Order order = sampleOrder();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("reserved", false)));
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

        assertThrows(RuntimeException.class, () -> orderService.reserveOrder("order-1"));
    }

    @Test
    void reserveOrder_shouldReleaseOnPartialFailure() {
        Order order = Order.builder()
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

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(restTemplate.postForEntity(contains("/reserve"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("reserved", true)))
                .thenReturn(ResponseEntity.ok(Map.of("reserved", false)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.reserveOrder("order-1");

        assertEquals("CANCELLED", response.getStatus());
        verify(restTemplate, times(1)).postForEntity(contains("/release"), eq(null), eq(Map.class));
    }

    @Test
    void updateStatus_shouldUpdateToPaid() {
        Order order = sampleOrder();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus("order-1", Order.OrderStatus.PAID);

        assertEquals("PAID", response.getStatus());
        assertNotNull(order.getPaidAt());
    }

    @Test
    void updateStatus_shouldUpdateToShipped() {
        Order order = sampleOrder();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus("order-1", Order.OrderStatus.SHIPPED);

        assertEquals("SHIPPED", response.getStatus());
        assertNotNull(order.getShippedAt());
    }

    @Test
    void updateStatus_shouldThrowWhenNotFound() {
        when(orderRepository.findById("999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> orderService.updateStatus("999", Order.OrderStatus.PAID));
    }
}
