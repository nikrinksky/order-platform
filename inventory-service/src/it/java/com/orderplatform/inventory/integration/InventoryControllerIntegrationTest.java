package com.orderplatform.inventory.integration;

import com.orderplatform.inventory.AbstractInventoryIntegrationTest;
import com.orderplatform.inventory.model.InventoryItem;
import com.orderplatform.inventory.repository.InventoryItemRepository;
import com.orderplatform.inventory.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private ReservationRepository reservationRepository;

    @BeforeEach
    void setUp() {
        // Reservations outlive a test otherwise, and the next reserve of the same order would be
        // answered idempotently instead of doing the work the test expects.
        reservationRepository.deleteAll();
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

    /**
     * The defect this guards: a retried reserve used to take the stock out of circulation twice,
     * because nothing remembered that the order had already reserved it.
     */
    @Test
    void shouldNotReserveTwiceForTheSameOrder() throws Exception {
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
                .andExpect(jsonPath("$.reserved").value(true));

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserved").value(true))
                .andExpect(jsonPath("$.message").value("Already reserved"));

        assertThat(inventoryItemRepository.findByProductId("prod-1"))
                .get()
                .extracting(InventoryItem::getReserved)
                .isEqualTo(10);
    }

    /**
     * The defect this guards: a repeated release subtracted the caller's quantity from the
     * aggregate again, freeing stock that belonged to a different order.
     */
    @Test
    void shouldNotReleaseTwice() throws Exception {
        inventoryItemRepository.save(InventoryItem.builder()
                .id("inv-1")
                .productId("prod-1")
                .quantity(100)
                .reserved(0)
                .available(true)
                .build());

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId": "order-1", "productId": "prod-1", "quantity": 10}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/inventory/release")
                        .param("orderId", "order-1")
                        .param("productId", "prod-1")
                        .param("quantity", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Released successfully"));

        mockMvc.perform(post("/api/inventory/release")
                        .param("orderId", "order-1")
                        .param("productId", "prod-1")
                        .param("quantity", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reservation is already RELEASED"));

        assertThat(inventoryItemRepository.findByProductId("prod-1"))
                .get()
                .extracting(InventoryItem::getReserved)
                .isEqualTo(0);
    }

    /**
     * The defect this guards: an order that reached COMPLETED kept its reservation, so the goods
     * stayed out of circulation for good.
     */
    @Test
    void shouldWriteOffStockWhenCommitted() throws Exception {
        inventoryItemRepository.save(InventoryItem.builder()
                .id("inv-1")
                .productId("prod-1")
                .quantity(100)
                .reserved(0)
                .available(true)
                .build());

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId": "order-1", "productId": "prod-1", "quantity": 10}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/inventory/commit")
                        .param("orderId", "order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(true))
                .andExpect(jsonPath("$.itemsCommitted").value(1));

        InventoryItem item = inventoryItemRepository.findByProductId("prod-1").orElseThrow();
        assertThat(item.getQuantity()).isEqualTo(90);
        assertThat(item.getReserved()).isZero();
    }

    /** A retry after a timeout must not write the same goods off a second time. */
    @Test
    void shouldNotCommitTwice() throws Exception {
        inventoryItemRepository.save(InventoryItem.builder()
                .id("inv-1")
                .productId("prod-1")
                .quantity(100)
                .reserved(0)
                .available(true)
                .build());

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId": "order-1", "productId": "prod-1", "quantity": 10}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/inventory/commit").param("orderId", "order-1"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/inventory/commit").param("orderId", "order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(true))
                .andExpect(jsonPath("$.itemsCommitted").value(0));

        InventoryItem item = inventoryItemRepository.findByProductId("prod-1").orElseThrow();
        assertThat(item.getQuantity()).isEqualTo(90);
        assertThat(item.getReserved()).isZero();
    }

    /** Releasing an order that reserved nothing must leave the stock of the other orders alone. */    @Test
    void shouldNotReleaseReservationOfAnotherOrder() throws Exception {
        inventoryItemRepository.save(InventoryItem.builder()
                .id("inv-1")
                .productId("prod-1")
                .quantity(100)
                .reserved(0)
                .available(true)
                .build());

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId": "order-1", "productId": "prod-1", "quantity": 10}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/inventory/release")
                        .param("orderId", "order-2")
                        .param("productId", "prod-1")
                        .param("quantity", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("No reservation to release"));

        assertThat(inventoryItemRepository.findByProductId("prod-1"))
                .get()
                .extracting(InventoryItem::getReserved)
                .isEqualTo(10);
    }
}