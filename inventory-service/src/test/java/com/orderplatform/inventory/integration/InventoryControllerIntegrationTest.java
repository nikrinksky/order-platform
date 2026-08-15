package com.orderplatform.inventory.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.inventory.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration тест для Inventory Controller.
 */
@AutoConfigureMockMvc
class InventoryControllerIntegrationTest extends AbstractInventoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryItemRepository inventoryRepository;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
    }

    @Test
    void shouldCreateOrUpdateInventory() throws Exception {
        String inventoryJson = """
                {
                    "productId": "product-123",
                    "quantity": 100,
                    "warehouseLocation": "Warehouse A"
                }
                """;

        mockMvc.perform(post("/api/inventory/product/product-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inventoryJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("product-123"))
                .andExpect(jsonPath("$.quantity").value(100));
    }

    @Test
    void shouldRejectNegativeQuantity() throws Exception {
        String inventoryJson = """
                {
                    "productId": "product-negative",
                    "quantity": -10,
                    "warehouseLocation": "Warehouse E"
                }
                """;

        mockMvc.perform(post("/api/inventory/product/product-negative")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inventoryJson))
                .andExpect(status().isBadRequest());
    }
}
