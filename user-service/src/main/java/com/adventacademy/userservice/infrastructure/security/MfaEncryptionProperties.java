package com.adventacademy.userservice.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * Versioned AES-256 keys for {@link AesGcmMfaSecretCipher}.
 * <pre>
 * security.mfa.encryption.active-key: v1
 * security.mfa.encryption.keys.v1: ${MFA_ENCRYPTION_KEY_V1}   # base64, 32 bytes
 * </pre>
 * Rotation: add {@code v2}, switch {@code active-key}, keep {@code v1} until every secret has been
 * re-encrypted (a user re-enrolling or a maintenance job), then drop it.
 */
@Validated
@ConfigurationProperties(prefix = "security.mfa.encryption")
public record MfaEncryptionProperties(
        @NotBlank String activeKey,
        @NotEmpty Map<String, String> keys
) {}
