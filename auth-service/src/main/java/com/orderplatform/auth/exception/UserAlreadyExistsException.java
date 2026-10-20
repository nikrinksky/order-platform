package com.orderplatform.auth.exception;

/**
 * Raised when a registration names an email that is already taken.
 *
 * <p>A dedicated type rather than a bare {@code RuntimeException}, so the advice can answer
 * {@code 400} for the duplicate the client caused instead of letting a {@code catch
 * (RuntimeException)} in the controller turn every other failure into the same response.
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
