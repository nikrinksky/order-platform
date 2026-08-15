package com.orderplatform.order.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.order.AbstractOrderIntegrationTest;
import com.orderplatform.order.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration тест для Order Controller.
 */
@AutoConfigureMockMvc
class OrderControllerIntegrationTest extends AbstractOrderIntegrationTest {
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateOrder() throws Exception {
        CreateOrderRequest.OrderItemRequest item = CreateOrderRequest.OrderItemRequest.builder()
                .productId("product-1")
                .productName("Test Product")
                .quantity(2)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId("test-user-123")
                .items(List.of(item))
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("test-user-123"))
                .andExpect(jsonPath("$.orderNumber").exists())
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void shouldRejectOrderWithoutItems() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .userId("test-user-empty")
                .items(List.of())
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
