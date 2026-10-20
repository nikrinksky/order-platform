package com.orderplatform.auth.exception;

/**
 * Raised when an operation names a user that does not exist.
 *
 * <p>A dedicated type rather than a bare {@code RuntimeException}: the advice has to answer
 * {@code 404} for a missing user while a genuine failure must not be disguised as one, and a
 * {@code catch (RuntimeException)} in the controller cannot tell the two apart.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
