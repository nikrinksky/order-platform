package com.orderplatform.inventory.integration;

import com.orderplatform.inventory.AbstractInventoryDatabaseTest;
import com.orderplatform.inventory.InventoryServiceApplication;
import com.orderplatform.inventory.model.InventoryItem;
import com.orderplatform.inventory.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the security of the reservation endpoints.
 *
 * <p>Unlike the other integration tests this one keeps the real {@code SecurityConfig} active, so
 * it exercises the actual filter chain instead of a permit-all test chain. It pins down the defect
 * where {@code /reserve} and {@code /release} were open to anonymous callers.
 *
 * <p>It therefore inherits only the database from {@link AbstractInventoryDatabaseTest}, not the
 * test security configuration the other integration tests pull in.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = InventoryServiceApplication.class)
@ActiveProfiles("integration-tests")
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.kafka.enabled=false"
})
class InventoryReservationSecurityIntegrationTest extends AbstractInventoryDatabaseTest {

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @BeforeEach
    void setUp() {
        inventoryItemRepository.deleteAll();
        inventoryItemRepository.save(InventoryItem.builder()
                .id("inv-1")
                .productId("prod-1")
                .quantity(100)
                .reserved(0)
                .available(true)
                .build());
    }

    @Test
    void shouldRejectAnonymousReservation() throws Exception {
        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveJson()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldRejectAnonymousRelease() throws Exception {
        mockMvc.perform(post("/api/inventory/release")
                        .param("orderId", "order-1")
                        .param("productId", "prod-1")
                        .param("quantity", "5"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "shopper", roles = "USER")
    void shouldForbidReservationForRegularUser() throws Exception {
        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void shouldAllowReservationForManager() throws Exception {
        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserved").value(true));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldAllowReleaseForAdmin() throws Exception {
        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reserveJson()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/inventory/release")
                        .param("orderId", "order-1")
                        .param("productId", "prod-1")
                        .param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserved").value(true));
    }

    private static String reserveJson() {
        return """
                {
                    "orderId": "order-1",
                    "productId": "prod-1",
                    "quantity": 5
                }
                """;
    }
}
