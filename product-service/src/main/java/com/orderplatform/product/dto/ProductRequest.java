package com.orderplatform.product.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive")
    @Digits(integer = 17, fraction = 2,
            message = "Price must be a number with at most 2 fraction digits")
    private BigDecimal price;

    private String category;

    @Min(value = 0, message = "Quantity must be non-negative")
    private Integer quantity;
}
