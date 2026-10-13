package com.adventacademy.userservice.application.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Random, opaque secrets that leave the service exactly once (in an e-mail or a one-time response)
 * and are stored only as a SHA-256 digest: verification/reset tokens and MFA recovery codes.
 */
public final class OpaqueTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_SAFE = Base64.getUrlEncoder().withoutPadding();
    private static final HexFormat HEX = HexFormat.of();
    /** Crockford-like alphabet without 0/O/1/I so the code survives being read aloud or typed. */
    private static final char[] RECOVERY_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int RECOVERY_GROUP = 5;

    private OpaqueTokens() {
    }

    /** @return 32 random bytes, URL-safe base64 (43 characters) */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_SAFE.encodeToString(bytes);
    }

    /** @return a recovery code such as {@code 7K3MQ-XW9RT}: 10 symbols from a 32-letter alphabet (50 bits) */
    public static String newRecoveryCode() {
        StringBuilder code = new StringBuilder(RECOVERY_GROUP * 2 + 1);
        for (int i = 0; i < RECOVERY_GROUP * 2; i++) {
            if (i == RECOVERY_GROUP) {
                code.append('-');
            }
            code.append(RECOVERY_ALPHABET[RANDOM.nextInt(RECOVERY_ALPHABET.length)]);
        }
        return code.toString();
    }

    /** Upper-cases and strips separators/whitespace so a hand-typed recovery code hashes the same. */
    public static String normalizeRecoveryCode(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    /** @return true when the value looks like a recovery code rather than a six-digit TOTP */
    public static boolean looksLikeRecoveryCode(String code) {
        return normalizeRecoveryCode(code).length() == RECOVERY_GROUP * 2;
    }

    /** @return lower-case hex SHA-256 of the value (64 characters) */
    public static String digest(String value) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(sha256.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
        }
    }
}
