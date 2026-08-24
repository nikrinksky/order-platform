package com.orderplatform.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.order.dto.CreateOrderRequest;
import com.orderplatform.order.dto.OrderResponse;
import com.orderplatform.order.model.Order;
import com.orderplatform.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for OrderController using @WebMvcTest.
 */
@WebMvcTest(controllers = OrderController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-1", null, List.of())
        );
    }

    private OrderResponse sampleResponse() {
        return OrderResponse.builder()
                .id("order-1")
                .userId("user-1")
                .orderNumber("ORD-ABC12345")
                .status("NEW")
                .totalAmount(BigDecimal.ZERO)
                .items(List.of())
                .build();
    }

    @Test
    void shouldCreateOrder() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId("user-1")
                .items(List.of(CreateOrderRequest.OrderItemRequest.builder()
                        .productId("prod-1")
                        .productName("Test")
                        .quantity(2)
                        .build()))
                .build();

        when(orderService.createOrder(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("user-1"));
    }

    @Test
    void shouldRejectOrderWithoutItems() throws Exception {
        String invalidJson = """
                {
                    "userId": "user-1",
                    "items": []
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReserveOrder() throws Exception {
        when(orderService.reserveOrder("order-1")).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/orders/order-1/reserve"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldUpdateStatus() throws Exception {
        when(orderService.updateStatus(anyString(), any())).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/orders/order-1/status")
                        .param("status", "PAID"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetOrderById() throws Exception {
        when(orderService.findById("order-1")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/orders/order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-1"));
    }

    @Test
    void shouldGetOrdersByUserId() throws Exception {
        when(orderService.findByUserId("user-1")).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/orders").param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user-1"));
    }
}