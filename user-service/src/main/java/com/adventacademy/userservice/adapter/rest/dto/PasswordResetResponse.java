package com.adventacademy.userservice.adapter.rest.dto;

/**
 * Response after successful password reset.
 * User is logged out (all refresh tokens revoked) and must log in again.
 */
public record PasswordResetResponse(
    String message,
    boolean success,
    long timestamp
) {
    public static PasswordResetResponse success() {
        return new PasswordResetResponse(
                "Password has been reset successfully. Please log in with your new password.",
                true,
                System.currentTimeMillis()
        );
    }

    public static PasswordResetResponse error(String message) {
        return new PasswordResetResponse(
                message,
                false,
                System.currentTimeMillis()
        );
    }
}
