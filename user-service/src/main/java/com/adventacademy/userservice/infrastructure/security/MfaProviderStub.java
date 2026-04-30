package com.adventacademy.userservice.infrastructure.security;

import com.adventacademy.userservice.application.port.MfaProvider;
import org.springframework.stereotype.Component;

@Component
public class MfaProviderStub implements MfaProvider {

    // TODO: Implement TOTP (Time-based One-Time Password) using Google Authenticator compatible library
    // Recommended library: dev.samstevens.totp:totp (Google Authenticator compatible)
    //
    // Example implementation:
    // - Use SecretGenerator to generate base32 secret
    // - Use QrGenerator to create QR code URL/data
    // - Use TimeProvider and CodeGenerator for TOTP generation
    // - Use CodeVerifier to validate codes (with time window)
    //
    // QR code format: otpauth://totp/UserService:email?secret=SECRET&issuer=UserService

    @Override
    public String generateSecret() {
        // TODO: Generate 32-character base32 secret
        // Example: SecretGenerator.generate()
        throw new UnsupportedOperationException("MFA secret generation not implemented yet. Please implement TOTP.");
    }

    @Override
    public String generateQrCodeUrl(String email, String secret) {
        // TODO: Generate QR code URL in format:
        // otpauth://totp/UserService:{email}?secret={secret}&issuer=UserService
        throw new UnsupportedOperationException("MFA QR code generation not implemented yet");
    }

    @Override
    public boolean validateCode(String secret, String code) {
        // TODO: Validate TOTP code against secret
        // Should accept codes from current time window and adjacent windows (to handle clock skew)
        throw new UnsupportedOperationException("MFA code validation not implemented yet");
    }
}
