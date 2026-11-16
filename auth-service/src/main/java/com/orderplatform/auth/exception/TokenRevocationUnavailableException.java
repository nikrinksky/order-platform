package com.orderplatform.auth.exception;

/**
 * Raised when a token could not be revoked because the blacklist is unreachable.
 *
 * <p>A dedicated type rather than a bare {@code RuntimeException}: a revocation that did not
 * happen must be reported as a server-side outage ({@code 503}), while an invalid token is a
 * client error ({@code 401}) and a genuine failure must not be disguised as either. The
 * controllers answer {@code 401} for everything else they catch, so they need to tell this one
 * apart.
 *
 * <p>The caller has to treat the operation as not done: the token is still usable, so a logout
 * that returned {@code 200} would be a lie the client has no way to notice.
 */
public class TokenRevocationUnavailableException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what went wrong, safe to log
     * @param cause   the underlying failure, usually a Redis connectivity error
     */
    public TokenRevocationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
