package com.adventacademy.userservice.domain.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditMetadataSanitizerTest {

    @Test
    void sanitize_redactsKeysContainingPassword() {
        Map<String, String> input = Map.of("oldPassword", "qwerty", "userId", "abc");
        Map<String, String> out = AuditMetadataSanitizer.sanitize(input);

        assertThat(out).containsEntry("oldPassword", "[REDACTED]");
        assertThat(out).containsEntry("userId", "abc");
    }

    @Test
    void sanitize_redactsTokensAccessAndRefresh() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("refreshToken", "rt_value");
        input.put("accessToken", "at_value");
        input.put("ip", "10.0.0.1");

        Map<String, String> out = AuditMetadataSanitizer.sanitize(input);

        assertThat(out).containsEntry("refreshToken", "[REDACTED]");
        assertThat(out).containsEntry("accessToken", "[REDACTED]");
        assertThat(out).containsEntry("ip", "10.0.0.1");
    }

    @Test
    void sanitize_redactsMfaSecretAndPasswordHash() {
        Map<String, String> input = Map.of("mfaSecret", "S3CR3T", "passwordHash", "bcrypt$$$");
        Map<String, String> out = AuditMetadataSanitizer.sanitize(input);

        assertThat(out.get("mfaSecret")).isEqualTo("[REDACTED]");
        assertThat(out.get("passwordHash")).isEqualTo("[REDACTED]");
    }

    @Test
    void sanitize_isCaseInsensitive() {
        Map<String, String> input = Map.of("CredentialBlob", "x", "MY_TOKEN", "y");
        Map<String, String> out = AuditMetadataSanitizer.sanitize(input);

        assertThat(out.get("CredentialBlob")).isEqualTo("[REDACTED]");
        assertThat(out.get("MY_TOKEN")).isEqualTo("[REDACTED]");
    }

    @Test
    void sanitize_passesThroughSafeKeys() {
        Map<String, String> input = Map.of("roleId", "r1", "roleName", "ADMIN", "email", "x@y.z");
        Map<String, String> out = AuditMetadataSanitizer.sanitize(input);

        assertThat(out).isEqualTo(input);
    }

    @Test
    void sanitize_handlesNullAndEmpty() {
        assertThat(AuditMetadataSanitizer.sanitize(null)).isNull();
        assertThat(AuditMetadataSanitizer.sanitize(Map.of())).isEmpty();
    }

    @Test
    void auditEventConstructor_appliesSanitizationByDefault() {
        Map<String, String> raw = Map.of("password", "leaked");
        AuditEvent event = new AuditEvent(
                java.util.UUID.randomUUID(),
                AuditEventType.PASSWORD_CHANGED,
                null, null, java.time.LocalDateTime.now(),
                null, null,
                AuditResult.SUCCESS, null,
                raw
        );

        assertThat(event.getMetadata().get("password")).isEqualTo("[REDACTED]");
    }
}
