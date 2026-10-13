package com.orderplatform.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    private String idempotencyKey;

    /**
     * Items of the order. {@code @Valid} is required here: without it the constraints declared on
     * {@link OrderItemRequest} are never evaluated and an order with a zero quantity, a blank
     * product or a missing quantity is accepted.
     */
    @Valid
    @NotEmpty(message = "Order must contain at least one item")
    private List<OrderItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        @NotBlank(message = "Product ID is required")
        private String productId;
        @NotBlank(message = "Product name is required")
        private String productName;
        /** {@code @Min} alone does not reject null, and a null quantity reached the pricing code. */
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
    }
}
