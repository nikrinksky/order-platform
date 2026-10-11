package com.orderplatform.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRequest {

    @NotBlank(message = "orderId is required")
    private String orderId;

    @NotBlank(message = "productId is required")
    private String productId;

    /**
     * Units to reserve. Must be strictly positive: a negative amount used to invert the
     * arithmetic and made the reserved counter go negative.
     */
    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than zero")
    private Integer quantity;
}
