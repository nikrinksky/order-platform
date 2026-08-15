package com.orderplatform.order.service;

import com.orderplatform.order.dto.CreateOrderRequest;
import com.orderplatform.order.dto.OrderResponse;
import com.orderplatform.order.model.Order;
import com.orderplatform.order.model.OrderItem;
import com.orderplatform.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
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

        Order savedOrder = sampleOrder();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response);
        assertEquals("user-1", response.getUserId());
        assertEquals("NEW", response.getStatus());
        verify(orderRepository, times(1)).save(any(Order.class));
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
        when(restTemplate.postForEntity(anyString(), any(), eq(Object.class)))
                .thenReturn(ResponseEntity.ok().build());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.reserveOrder("order-1");

        assertEquals("RESERVED", response.getStatus());
    }

    @Test
    void reserveOrder_shouldCancelWhenInventoryUnavailable() {
        Order order = sampleOrder();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(restTemplate.postForEntity(anyString(), any(), eq(Object.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
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
