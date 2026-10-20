package com.orderplatform.product.controller;

import com.orderplatform.product.dto.ProductRequest;
import com.orderplatform.product.dto.ProductResponse;
import com.orderplatform.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable("id") String id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @RequestParam(value = "page", defaultValue = "0") @Min(value = 0, message = "page must not be negative") int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") int size) {
        return ResponseEntity.ok(productService.findAll(page, size));
    }

    @GetMapping("/active")
    public ResponseEntity<List<ProductResponse>> getActiveProducts() {
        return ResponseEntity.ok(productService.findByActive(true));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<Page<ProductResponse>> getByCategory(
            @PathVariable("category") String category,
            @RequestParam(value = "page", defaultValue = "0") @Min(value = 0, message = "page must not be negative") int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") int size) {
        return ResponseEntity.ok(productService.findByCategory(category, page, size));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchByText(
            @RequestParam @NotBlank(message = "text must not be blank")
            @Size(max = 100, message = "text must not exceed 100 characters") String text,
            @RequestParam(value = "page", defaultValue = "0") @Min(value = 0, message = "page must not be negative") int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100") int size) {
        return ResponseEntity.ok(productService.searchByText(text, page, size));
    }

    @GetMapping("/price-range")
    public ResponseEntity<List<ProductResponse>> getByPriceRange(
            @RequestParam @DecimalMin(value = "0.0", message = "minPrice must not be negative") BigDecimal minPrice,
            @RequestParam @DecimalMin(value = "0.0", message = "maxPrice must not be negative") BigDecimal maxPrice) {
        return ResponseEntity.ok(productService.findByPriceRange(minPrice, maxPrice));
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(201).body(productService.createProduct(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable("id") String id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable("id") String id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ProductResponse> toggleActive(@PathVariable("id") String id) {
        return ResponseEntity.ok(productService.toggleActive(id));
    }
}
