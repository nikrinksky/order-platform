package com.orderplatform.order.config;

import com.orderplatform.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Security helper for order ownership checks.
 * Used in SpEL expressions like:
 * {@code @PreAuthorize("@orderSecurity.isOwner(#orderId, authentication.name)")}
 */
@Component
@RequiredArgsConstructor
public class OrderSecurity {

    private final OrderRepository orderRepository;

    /**
     * Checks if the given user is the owner of the specified order.
     *
     * @param orderId order ID
     * @param userId  user ID (from authentication.name, set by JWT filter)
     * @return true if the user owns the order
     */
    public boolean isOwner(String orderId, String userId) {
        return orderRepository.findById(orderId)
                .map(order -> order.getUserId().equals(userId))
                .orElse(false);
    }
}