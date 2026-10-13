package com.adventacademy.userservice.application.dto.response;

/**
 * Enrolment material shown once. {@code secret} is the plaintext seed for manual entry,
 * {@code qrCodeUrl} the {@code otpauth://} URI and {@code qrCodeImage} the same URI as a PNG data URL.
 */
public record EnableMfaResponse(
        String secret,
        String qrCodeUrl,
        String qrCodeImage
) {}
