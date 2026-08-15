package com.orderplatform.product.service;

import com.orderplatform.product.dto.ProductRequest;
import com.orderplatform.product.dto.ProductResponse;
import com.orderplatform.product.model.Product;
import com.orderplatform.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void createProduct_shouldReturnCreatedProduct() {
        ProductRequest request = ProductRequest.builder()
                .name("Test Product")
                .price(new BigDecimal("29.99"))
                .category("Electronics")
                .quantity(10)
                .build();

        Product savedProduct = Product.builder()
                .id("1")
                .name("Test Product")
                .price(new BigDecimal("29.99"))
                .category("Electronics")
                .quantity(10)
                .active(false)
                .build();

        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response);
        assertEquals("Test Product", response.getName());
        assertEquals(new BigDecimal("29.99"), response.getPrice());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void findById_shouldReturnProduct() {
        Product product = Product.builder()
                .id("1")
                .name("Test Product")
                .price(new BigDecimal("29.99"))
                .build();

        when(productRepository.findById("1")).thenReturn(Optional.of(product));

        ProductResponse response = productService.findById("1");

        assertNotNull(response);
        assertEquals("Test Product", response.getName());
    }

    @Test
    void findById_shouldThrowExceptionWhenNotFound() {
        when(productRepository.findById("999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> productService.findById("999"));
    }
}
