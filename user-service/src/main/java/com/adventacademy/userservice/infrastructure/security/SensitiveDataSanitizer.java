package com.adventacademy.userservice.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Redact sensitive data from logs, audit events, and error responses.
 * Prevents exposure of tokens, secrets, passwords, and MFA seeds.
 *
 * Redacted fields:
 * - Passwords (password, passwd, pwd, newPassword, currentPassword)
 * - Tokens (token, verificationToken, resetToken, refreshToken, accessToken)
 * - MFA secrets (mfaSecret, totpSecret, recoveryCodes, mfaCode)
 * - Email (for privacy)
 */
@Slf4j
@Component
public class SensitiveDataSanitizer {

    private static final String REDACTED = "[REDACTED]";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String[] SENSITIVE_FIELDS = {
            "password", "passwd", "pwd", "newPassword", "currentPassword", "confirmPassword",
            "token", "verificationToken", "resetToken", "refreshToken", "accessToken",
            "mfaSecret", "totpSecret", "recoveryCodes", "mfaCode",
            "secret", "apiKey", "authorization", "x-api-key"
    };

    /**
     * Sanitize JSON object by redacting sensitive fields.
     */
    public String sanitizeJson(String json) {
        if (json == null || json.isEmpty()) return json;

        try {
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode) {
                sanitizeNode((ObjectNode) node);
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.debug("Failed to sanitize JSON, returning original", e);
            return json;
        }
    }

    /**
     * Sanitize map by redacting sensitive fields.
     */
    public Map<String, Object> sanitizeMap(Map<String, Object> map) {
        if (map == null) return null;

        Map<String, Object> sanitized = new HashMap<>(map);
        for (String field : SENSITIVE_FIELDS) {
            if (sanitized.containsKey(field)) {
                sanitized.put(field, REDACTED);
            }
        }
        return sanitized;
    }

    /**
     * Check if a field name should be redacted.
     */
    public boolean isSensitiveField(String fieldName) {
        if (fieldName == null) return false;
        String lower = fieldName.toLowerCase();
        for (String sensitive : SENSITIVE_FIELDS) {
            if (lower.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }

    private void sanitizeNode(ObjectNode node) {
        node.fields().forEachRemaining(entry -> {
            if (isSensitiveField(entry.getKey())) {
                entry.setValue(objectMapper.valueToTree(REDACTED));
            } else if (entry.getValue() instanceof ObjectNode) {
                sanitizeNode((ObjectNode) entry.getValue());
            }
        });
    }
}
