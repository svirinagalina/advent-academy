package com.adventacademy.userservice.infrastructure.security;

import com.adventacademy.userservice.application.port.MfaSecretCipher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AES-256-GCM encryption of TOTP seeds at rest. Stored form: {@code <key version>:<nonce>:<ciphertext>}
 * (base64 for the two binary parts). The key version is bound into the GCM tag as associated data,
 * so a value cannot be re-labelled to another key.
 */
@Component
@EnableConfigurationProperties(MfaEncryptionProperties.class)
public class AesGcmMfaSecretCipher implements MfaSecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final Pattern KEY_VERSION = Pattern.compile("v[0-9]{1,4}");

    private final SecureRandom random = new SecureRandom();
    private final String activeVersion;
    private final Map<String, SecretKey> keys;

    public AesGcmMfaSecretCipher(MfaEncryptionProperties properties) {
        this.activeVersion = properties.activeKey();
        this.keys = properties.keys().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> toKey(e.getKey(), e.getValue())));
        if (!keys.containsKey(activeVersion)) {
            throw new IllegalArgumentException("security.mfa.encryption.active-key '" + activeVersion + "' has no key configured");
        }
    }

    @Override
    public String encrypt(String plaintextSecret) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeVersion), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(activeVersion.getBytes(StandardCharsets.US_ASCII));
            byte[] ciphertext = cipher.doFinal(plaintextSecret.getBytes(StandardCharsets.UTF_8));
            return activeVersion + ":" + Base64.getEncoder().encodeToString(nonce) + ":" + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("MFA secret encryption failed", e);
        }
    }

    @Override
    public String decrypt(String encryptedSecret) {
        if (encryptedSecret == null) {
            throw new IllegalArgumentException("No encrypted MFA secret");
        }
        String[] parts = encryptedSecret.split(":", 3);
        if (parts.length != 3 || !KEY_VERSION.matcher(parts[0]).matches()) {
            throw new IllegalArgumentException("Malformed encrypted MFA secret");
        }
        SecretKey key = keys.get(parts[0]);
        if (key == null) {
            throw new IllegalArgumentException("MFA secret encrypted with unknown key version " + parts[0]);
        }
        try {
            byte[] nonce = Base64.getDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(parts[0].getBytes(StandardCharsets.US_ASCII));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("MFA secret cannot be decrypted", e);
        }
    }

    /** @return true when the value was produced by {@link #encrypt} under a key we still hold */
    public boolean canDecrypt(String encryptedSecret) {
        try {
            decrypt(encryptedSecret);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static SecretKey toKey(String version, String base64) {
        if (!KEY_VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("MFA encryption key version must look like v1, got '" + version + "'");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("MFA encryption key " + version + " is not valid base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalArgumentException("MFA encryption key " + version + " must be 32 bytes (AES-256), got " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }
}
