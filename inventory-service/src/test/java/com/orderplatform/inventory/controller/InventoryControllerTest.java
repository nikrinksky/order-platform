package com.orderplatform.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.inventory.dto.ReservationRequest;
import com.orderplatform.inventory.dto.ReservationResponse;
import com.orderplatform.inventory.model.InventoryItem;
import com.orderplatform.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for InventoryController using @WebMvcTest.
 */
@WebMvcTest(controllers = InventoryController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    @Test
    void shouldGetInventoryByProductId() throws Exception {
        InventoryItem item = InventoryItem.builder()
                .id("1")
                .productId("prod-1")
                .quantity(100)
                .reserved(10)
                .available(true)
                .build();
        when(inventoryService.findByProductId("prod-1")).thenReturn(Optional.of(item));

        mockMvc.perform(get("/api/inventory/product/prod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("prod-1"))
                .andExpect(jsonPath("$.quantity").value(100));
    }

    @Test
    void shouldReturnNotFoundWhenProductNotExists() throws Exception {
        when(inventoryService.findByProductId("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/inventory/product/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReserveInventory() throws Exception {
        ReservationRequest request = ReservationRequest.builder()
                .orderId("order-1")
                .productId("prod-1")
                .quantity(5)
                .build();

        ReservationResponse response = ReservationResponse.builder()
                .orderId("order-1")
                .productId("prod-1")
                .quantity(5)
                .reserved(true)
                .message("Reserved successfully")
                .build();

        when(inventoryService.reserve(any())).thenReturn(response);

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserved").value(true))
                .andExpect(jsonPath("$.message").value("Reserved successfully"));
    }

    @Test
    void shouldCreateInventoryItem() throws Exception {
        InventoryItem item = InventoryItem.builder()
                .id("1")
                .productId("prod-1")
                .quantity(50)
                .reserved(0)
                .available(true)
                .build();
        when(inventoryService.createOrUpdate("prod-1", 50)).thenReturn(item);

        mockMvc.perform(post("/api/inventory")
                        .param("productId", "prod-1")
                        .param("quantity", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("prod-1"))
                .andExpect(jsonPath("$.quantity").value(50));
    }

    @Test
    void shouldReleaseInventory() throws Exception {
        ReservationResponse response = ReservationResponse.builder()
                .orderId("order-1")
                .productId("prod-1")
                .quantity(5)
                .reserved(true)
                .message("Released successfully")
                .build();
        when(inventoryService.release("order-1", "prod-1", 5)).thenReturn(response);

        mockMvc.perform(post("/api/inventory/release")
                        .param("orderId", "order-1")
                        .param("productId", "prod-1")
                        .param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserved").value(true));
    }

    @Test
    void shouldUpdateQuantity() throws Exception {
        doNothing().when(inventoryService).updateQuantity(anyString(), anyInt());

        mockMvc.perform(patch("/api/inventory/product/prod-1/quantity")
                        .param("quantity", "200"))
                .andExpect(status().isOk());
    }
}