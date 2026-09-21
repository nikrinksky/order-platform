package com.orderplatform.order.model;

import java.util.EnumSet;

public final class OrderStatusTransitions {

    private OrderStatusTransitions() {}

    private static final EnumSet<Order.OrderStatus> TERMINAL_STATES = EnumSet.of(
            Order.OrderStatus.COMPLETED, Order.OrderStatus.CANCELLED
    );

    public static boolean isTerminal(Order.OrderStatus status) {
        return TERMINAL_STATES.contains(status);
    }

    public static EnumSet<Order.OrderStatus> validNextStates(Order.OrderStatus current) {
        return switch (current) {
            case NEW -> EnumSet.of(Order.OrderStatus.RESERVED, Order.OrderStatus.CANCELLED);
            case RESERVED -> EnumSet.of(Order.OrderStatus.PAID, Order.OrderStatus.CANCELLED);
            case PAID -> EnumSet.of(Order.OrderStatus.SHIPPED, Order.OrderStatus.CANCELLED);
            case SHIPPED -> EnumSet.of(Order.OrderStatus.COMPLETED, Order.OrderStatus.CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(Order.OrderStatus.class);
        };
    }

    public static void validateTransition(Order.OrderStatus current, Order.OrderStatus target) {
        EnumSet<Order.OrderStatus> allowed = validNextStates(current);
        if (!allowed.contains(target)) {
            throw new IllegalArgumentException(
                    "Invalid status transition: " + current + " -> " + target
                            + ". Allowed: " + allowed);
        }
    }
}
