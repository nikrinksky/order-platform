package com.orderplatform.product.service;

import com.orderplatform.product.dto.ProductRequest;
import com.orderplatform.product.dto.ProductResponse;
import com.orderplatform.product.model.Product;
import com.orderplatform.product.repository.ProductRepository;
import com.orderplatform.product.security.CatalogAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    /**
     * Characters that carry a meaning inside a regular expression. A user supplied search string
     * containing one of them must be escaped before it reaches MongoDB.
     */
    private static final String REGEX_METACHARACTERS = "\\^$.|?*+()[]{}";

    private final ProductRepository productRepository;
    private final CatalogAccess catalogAccess;

    /**
     * Returns a product by id.
     *
     * <p>A hidden product is reported as missing unless the caller may see hidden products: the
     * endpoint is public, so without this an unpublished product was readable by anyone who knew
     * its id.
     */
    public ProductResponse findById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> notFound(id));
        if (!product.isActive() && !catalogAccess.canViewHiddenProducts()) {
            throw notFound(id);
        }
        return toResponse(product);
    }

    /** Lists the catalogue: the published products for a visitor, everything for a privileged caller. */
    public Page<ProductResponse> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> products = catalogAccess.canViewHiddenProducts()
                ? productRepository.findAll(pageable)
                : productRepository.findByActive(true, pageable);
        return products.map(this::toResponse);
    }

    public List<ProductResponse> findByActive(boolean active) {
        return productRepository.findByActive(active).stream()
                .map(this::toResponse)
                .toList();
    }

    public Page<ProductResponse> findByCategory(String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = catalogAccess.canViewHiddenProducts()
                ? productRepository.findByCategory(category, pageable)
                : productRepository.findByCategoryAndActive(category, true, pageable);
        return products.map(this::toResponse);
    }

    public Page<ProductResponse> searchByText(String text, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        // The user string is a literal to search for, not a regular expression: escape it so that
        // ".*" or a nested quantifier cannot be used to dump or stall the collection.
        String escaped = escapeRegex(text);
        Page<Product> products = catalogAccess.canViewHiddenProducts()
                ? productRepository.searchByText(escaped, pageable)
                : productRepository.searchActiveByText(escaped, pageable);
        return products.map(this::toResponse);
    }

    public List<ProductResponse> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        List<Product> products = catalogAccess.canViewHiddenProducts()
                ? productRepository.findByPriceRangeIncludingHidden(minPrice, maxPrice)
                : productRepository.findByPriceRange(minPrice, maxPrice);
        return products.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Turns a user supplied search string into a literal regular expression: every character that
     * has a special meaning in a regex is prefixed with a backslash.
     *
     * @param text raw search text
     * @return the escaped text, safe to use as a {@code $regex} operand
     */
    static String escapeRegex(String text) {
        StringBuilder escaped = new StringBuilder(text.length() * 2);
        for (char c : text.toCharArray()) {
            if (REGEX_METACHARACTERS.indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    private static RuntimeException notFound(String id) {
        return new RuntimeException("Product not found with id: " + id);
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .quantity(request.getQuantity() != null ? request.getQuantity() : 0)
                .active(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        product = productRepository.save(product);
        return toResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(String id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> notFound(id));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setQuantity(request.getQuantity() != null ? request.getQuantity() : 0);
        product.setUpdatedAt(LocalDateTime.now());

        product = productRepository.save(product);
        return toResponse(product);
    }

    @Transactional
    public void deleteProduct(String id) {
        if (!productRepository.existsById(id)) {
            throw notFound(id);
        }
        productRepository.deleteById(id);
    }

    @Transactional
    public ProductResponse toggleActive(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> notFound(id));

        product.setActive(!product.isActive());
        product.setUpdatedAt(LocalDateTime.now());

        product = productRepository.save(product);
        return toResponse(product);
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory())
                .quantity(product.getQuantity())
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
