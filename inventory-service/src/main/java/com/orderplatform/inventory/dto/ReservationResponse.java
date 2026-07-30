package com.orderplatform.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    private String orderId;
    private String productId;
    private Integer quantity;
    private boolean reserved;
    private String message;
}
