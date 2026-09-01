package com.adventacademy.userservice.infrastructure.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrDataFactory;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TotpProvider {
    private final SecretGenerator secretGenerator;
    private final CodeGenerator codeGenerator;
    private final CodeVerifier codeVerifier;
    private final TimeProvider timeProvider;
    private final int windowSize;

    // Store last used TOTP to prevent replay attacks: secret -> (code, timestamp)
    private final Map<String, String> lastUsedCodes = new ConcurrentHashMap<>();

    public TotpProvider(
            @Value("${security.mfa.totp-window-size:1}") int windowSize
    ) {
        this.secretGenerator = new DefaultSecretGenerator(256);  // 256-bit secret = 32 chars base32
        this.codeGenerator = new DefaultCodeGenerator();
        this.codeVerifier = new DefaultCodeVerifier();
        this.timeProvider = new SystemTimeProvider();
        this.windowSize = windowSize;
    }

    /**
     * Generate a cryptographically random TOTP secret (32-character base32 string).
     * Compatible with Google Authenticator, Authy, Microsoft Authenticator, etc.
     */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /**
     * Generate QR code URL for manual entry into authenticator apps.
     * Format: otpauth://totp/UserService:email?secret=...&issuer=...
     */
    public String generateQrCodeUrl(String email, String secret) {
        try {
            QrData qrData = new QrDataFactory()
                    .newBuilder()
                    .label(email)
                    .secret(secret)
                    .issuer("UserService")
                    .accountName(email)
                    .build();

            return qrData.getUri();
        } catch (QrGenerationException e) {
            log.error("Failed to generate QR code URL", e);
            throw new RuntimeException("QR code generation failed", e);
        }
    }

    /**
     * Generate QR code as PNG image (base64 encoded data URL).
     * Useful for embedding in HTML/JSON responses.
     */
    public String generateQrCodeImage(String email, String secret) {
        try {
            String qrUri = generateQrCodeUrl(email, secret);

            // Generate QR code bitmap
            BitMatrix bitMatrix = new MultiFormatWriter()
                    .encode(qrUri, BarcodeFormat.QR_CODE, 200, 200);

            // Convert to PNG bytes
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);

            // Encode as data URL
            byte[] pngBytes = pngOutputStream.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(pngBytes);
            return "data:image/png;base64," + base64;
        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR code image", e);
            throw new RuntimeException("QR code image generation failed", e);
        }
    }

    /**
     * Validate TOTP code against secret.
     *
     * Checks:
     * - Code is valid for current time window (±windowSize * 30 seconds)
     * - Code has not been used before (replay prevention)
     * - Code length is exactly 6 digits
     *
     * @param secret The base32-encoded TOTP secret
     * @param code The 6-digit TOTP code from authenticator app
     * @return true if code is valid and fresh, false otherwise
     */
    public boolean validateCode(String secret, String code) {
        if (secret == null || code == null || code.length() != 6) {
            log.debug("Invalid TOTP code format");
            return false;
        }

        try {
            // Verify code is valid for current time (with ±windowSize window)
            boolean isCodeValid = codeVerifier.isValidCode(secret, code, windowSize);
            if (!isCodeValid) {
                log.debug("TOTP code validation failed for time window");
                return false;
            }

            // Check for replay: ensure this code wasn't used before
            String codeKey = secret + ":" + code;
            if (lastUsedCodes.containsKey(codeKey)) {
                log.warn("TOTP replay attack detected: code reused for secret");
                return false;
            }

            // Mark code as used (prevent replay)
            lastUsedCodes.put(codeKey, code);

            log.debug("TOTP code validated successfully");
            return true;
        } catch (CodeGenerationException e) {
            log.error("TOTP code generation error during validation", e);
            return false;
        }
    }

    /**
     * Generate current TOTP code for testing/debugging purposes.
     * Should NOT be used in production for user verification.
     */
    public String getCurrentCode(String secret) {
        try {
            return codeGenerator.generate(secret);
        } catch (CodeGenerationException e) {
            log.error("Failed to generate current TOTP code", e);
            throw new RuntimeException("TOTP code generation failed", e);
        }
    }

    /**
     * Check if a code has been used before (for replay prevention).
     */
    public boolean isCodeUsed(String secret, String code) {
        String codeKey = secret + ":" + code;
        return lastUsedCodes.containsKey(codeKey);
    }

    /**
     * Clear all cached used codes. Should be called periodically to free memory.
     * Called during application shutdown or maintenance.
     */
    public void clearUsedCodes() {
        lastUsedCodes.clear();
        log.info("Cleared {} cached TOTP codes", lastUsedCodes.size());
    }

    /**
     * Get time-based info for debugging (current timestamp, window size).
     */
    public Map<String, Object> getDebugInfo(String secret, String code) {
        Map<String, Object> info = new HashMap<>();
        try {
            String currentCode = getCurrentCode(secret);
            info.put("current_code", currentCode);
            info.put("provided_code", code);
            info.put("match", currentCode.equals(code));
            info.put("window_size", windowSize);
            info.put("time_step_seconds", 30);
        } catch (Exception e) {
            info.put("error", e.getMessage());
        }
        return info;
    }
}
