package com.adventacademy.userservice.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Strips potentially sensitive values from audit metadata.
 * <p>
 * Any entry whose key contains one of the deny-listed substrings (case-insensitive)
 * has its value replaced with {@value #REDACTED}. The key is preserved so the
 * audit trail still shows that a field was present, just not its content.
 * </p>
 */
public final class AuditMetadataSanitizer {

    public static final String REDACTED = "[REDACTED]";

    private static final String[] DENY_LIST = {
            "password",
            "token",
            "secret",
            "credential",
            "hash"
    };

    private AuditMetadataSanitizer() {
    }

    public static Map<String, String> sanitize(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return metadata;
        }
        Map<String, String> sanitized = new LinkedHashMap<>(metadata.size());
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            sanitized.put(entry.getKey(), isSensitive(entry.getKey()) ? REDACTED : entry.getValue());
        }
        return sanitized;
    }

    private static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        for (String needle : DENY_LIST) {
            if (lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
