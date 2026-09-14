package com.adventacademy.userservice.domain.exception;

/**
 * A verification or reset token was presented that cannot be honoured: unknown, expired, already
 * used, or issued for another purpose. The public message is deliberately the same for all four
 * cases; the precise reason only goes to the audit trail.
 */
public class InvalidTokenException extends RuntimeException {

    private final String reason;

    public InvalidTokenException(String reason) {
        super("Invalid or expired token");
        this.reason = reason;
    }

    /** Machine-readable cause for the audit trail: {@code unknown}, {@code expired}, {@code used}, {@code wrong_purpose}. */
    public String getReason() {
        return reason;
    }
}
