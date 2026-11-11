package com.orderplatform.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outcome of writing off the stock an order held.
 *
 * <p>{@code committed} says the order no longer holds anything, which is also the answer for a
 * repeated call: committing twice is a success that writes off nothing the second time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitResponse {

    private String orderId;

    /** True when the order holds no reservation any more. */
    private boolean committed;

    /** How many reservations this call actually wrote off; zero on a repeat. */
    private int itemsCommitted;

    private String message;
}
