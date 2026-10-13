package com.adventacademy.userservice.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmMfaSecretCipherTest {

    private static final String KEY_V1 = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String KEY_V2 = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final AesGcmMfaSecretCipher cipher = new AesGcmMfaSecretCipher(new MfaEncryptionProperties("v1", Map.of("v1", KEY_V1)));

    @Test
    void roundTripsAndNeverStoresThePlaintext() {
        String encrypted = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(encrypted).startsWith("v1:").doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(encrypted.split(":")).hasSize(3);
        assertThat(cipher.decrypt(encrypted)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void everyEncryptionUsesAFreshNonce() {
        assertThat(cipher.encrypt("SAME")).isNotEqualTo(cipher.encrypt("SAME"));
    }

    @Test
    void oldKeyVersionsStayReadableAfterRotationAndNewSecretsUseTheActiveKey() {
        String underV1 = cipher.encrypt("SEED");
        AesGcmMfaSecretCipher rotated = new AesGcmMfaSecretCipher(
                new MfaEncryptionProperties("v2", Map.of("v1", KEY_V1, "v2", KEY_V2)));

        assertThat(rotated.decrypt(underV1)).isEqualTo("SEED");
        assertThat(rotated.encrypt("SEED")).startsWith("v2:");
    }

    @Test
    void tamperedCiphertextRelabelledVersionAndUnknownKeysAreRefused() {
        String encrypted = cipher.encrypt("SEED");
        String[] parts = encrypted.split(":");

        assertThatThrownBy(() -> cipher.decrypt(parts[0] + ":" + parts[1] + ":" + parts[2].substring(0, parts[2].length() - 2) + "AA"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher.decrypt("v9:" + parts[1] + ":" + parts[2])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher.decrypt("plaintext-legacy-secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher.decrypt(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(cipher.canDecrypt(encrypted)).isTrue();
        assertThat(cipher.canDecrypt("JBSWY3DPEHPK3PXP")).isFalse();
    }

    @Test
    void misconfiguredKeysFailAtStartupNotAtFirstUse() {
        assertThatThrownBy(() -> new AesGcmMfaSecretCipher(new MfaEncryptionProperties("v2", Map.of("v1", KEY_V1))))
                .hasMessageContaining("active-key");
        assertThatThrownBy(() -> new AesGcmMfaSecretCipher(new MfaEncryptionProperties("v1", Map.of("v1", "c2hvcnQ="))))
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new AesGcmMfaSecretCipher(new MfaEncryptionProperties("v1", Map.of("v1", "not base64!"))))
                .hasMessageContaining("base64");
    }
}
