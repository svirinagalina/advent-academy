package com.adventacademy.userservice.infrastructure.security;

import com.adventacademy.userservice.application.port.MfaProvider;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.OptionalLong;

/**
 * RFC 6238 TOTP (SHA-1, 6 digits, 30-second steps) compatible with Google Authenticator, Authy and
 * Microsoft Authenticator.
 * <p>
 * Verification reports the matching time step instead of a plain boolean so the application can
 * keep the last accepted step per account and refuse replays without any process-local cache.
 * Time comes from the injected {@link Clock}, which is what makes the window boundaries testable.
 */
@Component
public class TotpProvider implements MfaProvider {

    public static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int SECRET_LENGTH_CHARS = 32; // 32 base32 chars = 160 bits
    private static final String ISSUER = "Advent Academy";

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(SECRET_LENGTH_CHARS);
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, CODE_DIGITS);
    private final Clock clock;
    private final int windowSize;

    public TotpProvider(@Value("${security.mfa.totp-window-size:1}") int windowSize, Clock clock) {
        if (windowSize < 0 || windowSize > 2) {
            throw new IllegalArgumentException("security.mfa.totp-window-size must be between 0 and 2");
        }
        this.windowSize = windowSize;
        this.clock = clock;
    }

    @Override
    public String generateSecret() {
        return secretGenerator.generate();
    }

    @Override
    public String generateQrCodeUrl(String email, String secret) {
        return new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(CODE_DIGITS)
                .period(TIME_STEP_SECONDS)
                .build()
                .getUri();
    }

    @Override
    public String generateQrCodeImage(String email, String secret) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(generateQrCodeUrl(email, secret), BarcodeFormat.QR_CODE, 200, 200);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("QR code image generation failed", e);
        }
    }

    @Override
    public OptionalLong verifyCode(String secret, String code) {
        if (secret == null || code == null || code.length() != CODE_DIGITS || !code.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        long currentStep = Math.floorDiv(clock.instant().getEpochSecond(), TIME_STEP_SECONDS);
        // Check every step in the window so timing does not reveal which one matched.
        OptionalLong matched = OptionalLong.empty();
        for (long step = currentStep - windowSize; step <= currentStep + windowSize; step++) {
            if (matches(secret, step, code) && matched.isEmpty()) {
                matched = OptionalLong.of(step);
            }
        }
        return matched;
    }

    /** Test/support helper: the code the authenticator app shows for the given time step. */
    public String codeForStep(String secret, long step) {
        try {
            return codeGenerator.generate(secret, step);
        } catch (CodeGenerationException e) {
            throw new IllegalStateException("TOTP code generation failed", e);
        }
    }

    private boolean matches(String secret, long step, String code) {
        return MessageDigest.isEqual(
                codeForStep(secret, step).getBytes(StandardCharsets.US_ASCII),
                code.getBytes(StandardCharsets.US_ASCII));
    }
}
