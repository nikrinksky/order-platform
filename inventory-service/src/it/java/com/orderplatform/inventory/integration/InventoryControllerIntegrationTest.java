package com.orderplatform.inventory.integration;

import com.orderplatform.inventory.AbstractInventoryIntegrationTest;
import com.orderplatform.inventory.model.InventoryItem;
import com.orderplatform.inventory.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for InventoryController.
 */
@AutoConfigureMockMvc
class InventoryControllerIntegrationTest extends AbstractInventoryIntegrationTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @BeforeEach
    void setUp() {
        inventoryItemRepository.deleteAll();
    }

    @Test
    void shouldCreateInventoryItem() throws Exception {
        mockMvc.perform(post("/api/inventory")
                        .param("productId", "prod-1")
                        .param("quantity", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("prod-1"))
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void shouldGetInventoryByProductId() throws Exception {
        inventoryItemRepository.save(InventoryItem.builder()
                .id("inv-1")
                .productId("prod-1")
                .quantity(50)
                .reserved(0)
                .available(true)
                .build());

        mockMvc.perform(get("/api/inventory/product/prod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(50));
    }

    @Test
    void shouldReturnNotFoundForUnknownProduct() throws Exception {
        mockMvc.perform(get("/api/inventory/product/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReserveInventory() throws Exception {
        inventoryItemRepository.save(InventoryItem.builder()
                .id("inv-1")
                .productId("prod-1")
                .quantity(100)
                .reserved(0)
                .available(true)
                .build());

        String reserveJson = """
                {
                    "orderId": "order-1",
                    "productId": "prod-1",
                    "quantity": 10
                }
                """;

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserved").value(true))
                .andExpect(jsonPath("$.message").value("Reserved successfully"));
    }

    @Test
    void shouldFailReserveWhenInsufficientInventory() throws Exception {
        inventoryItemRepository.save(InventoryItem.builder()
                .id("inv-1")
                .productId("prod-1")
                .quantity(5)
                .reserved(0)
                .available(true)
                .build());

        String reserveJson = """
                {
                    "orderId": "order-1",
                    "productId": "prod-1",
                    "quantity": 10
                }
                """;

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserved").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Insufficient")));
    }
}