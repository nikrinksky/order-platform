package com.orderplatform.product.service;

/**
 * Thrown when a product does not exist or is hidden from the caller.
 *
 * <p>It exists so the two transports can tell a missing product from a broken database: the REST
 * advice answers {@code 404}, and the gRPC endpoint answers {@code NOT_FOUND}, while anything else
 * stays an internal error. A bare {@link RuntimeException} made both look the same.
 */
public class ProductNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProductNotFoundException(String message) {
        super(message);
    }
}
