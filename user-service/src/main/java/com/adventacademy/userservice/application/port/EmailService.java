package com.adventacademy.userservice.application.port;

/**
 * Port for sending transactional emails.
 * Production implementation: SMTP via Spring Mail.
 * Test implementation: In-memory mock.
 */
public interface EmailService {

    /**
     * Send email verification link.
     * Email should contain a link with the verification token.
     */
    void sendEmailVerification(String email, String verificationToken);

    /**
     * Send password reset link.
     * Email should contain a link with the password reset token.
     */
    void sendPasswordReset(String email, String resetToken);

    /**
     * Send MFA recovery codes (shown once during enrollment).
     * Email should contain the 8 recovery codes.
     */
    void sendRecoveryCodes(String email, java.util.List<String> recoveryCodes);

    /**
     * Exception for email sending failures.
     */
    class SendEmailException extends RuntimeException {
        public SendEmailException(String message) {
            super(message);
        }

        public SendEmailException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
