package com.adventacademy.userservice.application.port;

import java.util.OptionalLong;

/**
 * TOTP provider (RFC 6238) behind the MFA use-cases.
 * <p>
 * Secrets handed in and out are the plaintext base32 seed; encrypting them for storage is the job
 * of {@link MfaSecretCipher}, never of the provider.
 */
public interface MfaProvider {

    /** @return a fresh, cryptographically random base32 secret */
    String generateSecret();

    /** @return the {@code otpauth://} URI authenticator apps import */
    String generateQrCodeUrl(String email, String secret);

    /** @return the same URI rendered as a PNG {@code data:} URL */
    String generateQrCodeImage(String email, String secret);

    /**
     * Checks the code against the secret within the configured time window.
     *
     * @return the time step the code was generated for when it is valid, empty otherwise. The
     *         caller compares the step with the last accepted one to refuse replays.
     */
    OptionalLong verifyCode(String secret, String code);
}
