package com.orderplatform.product.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.product.AbstractProductIntegrationTest;
import com.orderplatform.product.model.Product;
import com.orderplatform.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration тест для Product Controller.
 */
@AutoConfigureMockMvc
class ProductControllerIntegrationTest extends AbstractProductIntegrationTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    void shouldCreateProduct() throws Exception {
        String productJson = """
                {
                    "name": "Test Product",
                    "description": "Test Description",
                    "price": 99.99,
                    "category": "Electronics",
                    "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Product"))
                .andExpect(jsonPath("$.price").value(99.99));
    }

    @Test
    void shouldRejectProductWithoutName() throws Exception {
        String productJson = """
                {
                    "name": "",
                    "description": "Test",
                    "price": 99.99,
                    "quantity": 100
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson))
                .andExpect(status().isBadRequest());
    }
}
