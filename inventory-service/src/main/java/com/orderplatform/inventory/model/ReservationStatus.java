package com.orderplatform.inventory.model;

/**
 * Lifecycle of a stock reservation.
 *
 * <p>The state is what makes the reservation endpoints idempotent: a repeated {@code reserve} finds
 * the {@code ACTIVE} row and leaves the stock alone, and a repeated {@code release} finds a row that
 * is no longer {@code ACTIVE} and does nothing. Without it the counters were adjusted on every call
 * and one retried request could reserve - or give away - the stock twice.
 */
public enum ReservationStatus {

    /** Stock is currently held for the order. */
    ACTIVE,

    /** The hold was given back; the stock is free again. */
    RELEASED,

    /** The goods left the warehouse: the stock was written off, not given back. */
    COMMITTED
}
