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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct(String id, String name) {
        return Product.builder()
                .id(id)
                .name(name)
                .description("Description")
                .price(new BigDecimal("29.99"))
                .category("Electronics")
                .quantity(10)
                .active(true)
                .build();
    }

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
    void createProduct_shouldDefaultQuantityToZeroWhenNull() {
        ProductRequest request = ProductRequest.builder()
                .name("Test Product")
                .price(new BigDecimal("29.99"))
                .quantity(null)
                .build();

        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.createProduct(request);

        assertEquals(0, response.getQuantity());
        assertFalse(response.isActive());
    }

    @Test
    void findById_shouldReturnProduct() {
        when(productRepository.findById("1")).thenReturn(Optional.of(sampleProduct("1", "Test Product")));

        ProductResponse response = productService.findById("1");

        assertNotNull(response);
        assertEquals("Test Product", response.getName());
    }

    @Test
    void findById_shouldThrowExceptionWhenNotFound() {
        when(productRepository.findById("999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> productService.findById("999"));
    }

    @Test
    void findAll_shouldReturnPage() {
        Page<Product> page = new PageImpl<>(List.of(sampleProduct("1", "P1"), sampleProduct("2", "P2")));
        when(productRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<ProductResponse> result = productService.findAll(0, 20);

        assertEquals(2, result.getContent().size());
        assertEquals("P1", result.getContent().get(0).getName());
    }

    @Test
    void findByActive_shouldReturnActiveProducts() {
        when(productRepository.findByActive(true)).thenReturn(List.of(sampleProduct("1", "P1")));

        List<ProductResponse> result = productService.findByActive(true);

        assertEquals(1, result.size());
        assertEquals("P1", result.get(0).getName());
    }

    @Test
    void findByCategory_shouldReturnPage() {
        Page<Product> page = new PageImpl<>(List.of(sampleProduct("1", "P1")));
        when(productRepository.findByCategory(eq("Electronics"), any(Pageable.class))).thenReturn(page);

        Page<ProductResponse> result = productService.findByCategory("Electronics", 0, 20);

        assertEquals(1, result.getContent().size());
    }

    @Test
    void searchByText_shouldReturnPage() {
        Page<Product> page = new PageImpl<>(List.of(sampleProduct("1", "P1")));
        when(productRepository.searchByText(eq("phone"), any(Pageable.class))).thenReturn(page);

        Page<ProductResponse> result = productService.searchByText("phone", 0, 20);

        assertEquals(1, result.getContent().size());
    }

    @Test
    void findByPriceRange_shouldReturnProducts() {
        when(productRepository.findByPriceRange(any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(List.of(sampleProduct("1", "P1")));

        List<ProductResponse> result = productService.findByPriceRange(
                new BigDecimal("10"), new BigDecimal("100"));

        assertEquals(1, result.size());
    }

    @Test
    void updateProduct_shouldUpdateExistingProduct() {
        Product existing = sampleProduct("1", "Old");
        when(productRepository.findById("1")).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = ProductRequest.builder()
                .name("New Name")
                .price(new BigDecimal("99.99"))
                .quantity(5)
                .build();

        ProductResponse response = productService.updateProduct("1", request);

        assertEquals("New Name", response.getName());
        assertEquals(new BigDecimal("99.99"), response.getPrice());
        assertEquals(5, response.getQuantity());
    }

    @Test
    void updateProduct_shouldThrowWhenNotFound() {
        when(productRepository.findById("999")).thenReturn(Optional.empty());

        ProductRequest request = ProductRequest.builder().name("X").price(new BigDecimal("1")).build();

        assertThrows(RuntimeException.class, () -> productService.updateProduct("999", request));
    }

    @Test
    void deleteProduct_shouldDeleteExistingProduct() {
        when(productRepository.existsById("1")).thenReturn(true);

        productService.deleteProduct("1");

        verify(productRepository, times(1)).deleteById("1");
    }

    @Test
    void deleteProduct_shouldThrowWhenNotFound() {
        when(productRepository.existsById("999")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> productService.deleteProduct("999"));
        verify(productRepository, never()).deleteById(anyString());
    }

    @Test
    void toggleActive_shouldFlipActiveState() {
        Product product = sampleProduct("1", "P1");
        product.setActive(false);
        when(productRepository.findById("1")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.toggleActive("1");

        assertTrue(response.isActive());
    }

    @Test
    void toggleActive_shouldThrowWhenNotFound() {
        when(productRepository.findById("999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> productService.toggleActive("999"));
    }

    @Test
    void findAll_shouldMapAllFields() {
        Product product = Product.builder()
                .id("1")
                .name("P1")
                .description("Desc")
                .price(new BigDecimal("9.99"))
                .category("Books")
                .quantity(3)
                .active(false)
                .build();
        when(productRepository.findByActive(anyBoolean())).thenReturn(List.of(product));

        ProductResponse response = productService.findByActive(false).get(0);

        assertEquals("1", response.getId());
        assertEquals("Desc", response.getDescription());
        assertEquals("Books", response.getCategory());
        assertEquals(3, response.getQuantity());
        assertFalse(response.isActive());
    }
}
