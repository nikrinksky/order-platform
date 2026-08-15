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
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private OrderService orderService;

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

        Order savedOrder = Order.builder()
                .id("order-1")
                .userId("user-1")
                .orderNumber("ORD-ABC12345")
                .status(Order.OrderStatus.NEW)
                .totalAmount(BigDecimal.ZERO)
                .build();

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response);
        assertEquals("user-1", response.getUserId());
        assertEquals("NEW", response.getStatus());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void findById_shouldReturnOrder() {
        Order order = Order.builder()
                .id("order-1")
                .userId("user-1")
                .orderNumber("ORD-ABC12345")
                .status(Order.OrderStatus.NEW)
                .build();

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        OrderResponse response = orderService.findById("order-1");

        assertNotNull(response);
        assertEquals("order-1", response.getId());
    }

    @Test
    void findById_shouldThrowExceptionWhenNotFound() {
        when(orderRepository.findById("999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> orderService.findById("999"));
    }
}
