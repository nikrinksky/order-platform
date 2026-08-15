package com.orderplatform.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.product.dto.ProductRequest;
import com.orderplatform.product.dto.ProductResponse;
import com.orderplatform.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ProductController using @WebMvcTest.
 */
@WebMvcTest(controllers = ProductController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private ProductResponse sampleResponse() {
        return ProductResponse.builder()
                .id("1")
                .name("Test Product")
                .description("Description")
                .price(new BigDecimal("29.99"))
                .category("Electronics")
                .quantity(10)
                .active(true)
                .build();
    }

    @Test
    void shouldGetProductById() throws Exception {
        when(productService.findById("1")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Product"))
                .andExpect(jsonPath("$.price").value(29.99));
    }

    @Test
    void shouldGetAllProducts() throws Exception {
        when(productService.findAll(anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Test Product"));
    }

    @Test
    void shouldGetActiveProducts() throws Exception {
        when(productService.findByActive(true)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/products/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Product"));
    }

    @Test
    void shouldGetByCategory() throws Exception {
        when(productService.findByCategory(eq("Electronics"), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/products/category/Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].category").value("Electronics"));
    }

    @Test
    void shouldSearchByText() throws Exception {
        when(productService.searchByText(eq("phone"), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/products/search").param("text", "phone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Test Product"));
    }

    @Test
    void shouldGetByPriceRange() throws Exception {
        when(productService.findByPriceRange(any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/products/price-range")
                        .param("minPrice", "10")
                        .param("maxPrice", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Product"));
    }

    @Test
    void shouldCreateProduct() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("New Product")
                .price(new BigDecimal("49.99"))
                .quantity(5)
                .build();

        when(productService.createProduct(any(ProductRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Product"));
    }

    @Test
    void shouldRejectCreateWithoutName() throws Exception {
        String invalidJson = """
                {
                    "price": 29.99
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateProduct() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Updated")
                .price(new BigDecimal("99.99"))
                .quantity(3)
                .build();

        when(productService.updateProduct(eq("1"), any(ProductRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Product"));
    }

    @Test
    void shouldDeleteProduct() throws Exception {
        doNothing().when(productService).deleteProduct("1");

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldToggleActive() throws Exception {
        when(productService.toggleActive("1")).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/products/1/toggle-active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }
}