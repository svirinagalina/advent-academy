package com.adventacademy.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Short-lived, operation-bound assertions between trusted application components. */
public final class SignedRequest {
    private SignedRequest() { }

    public static String sign(String key, String purpose, String value) {
        long seconds = Instant.now().getEpochSecond();
        return seconds + "." + mac(key, purpose + "\n" + value + "\n" + seconds);
    }

    public static boolean verify(String key, String purpose, String value, String signature) {
        if (key == null || key.length() < 32 || signature == null || signature.length() > 100) { return false; }
        try {
            String[] parts = signature.split("\\.", -1);
            if (parts.length != 2 || Math.abs(Instant.now().getEpochSecond() - Long.parseLong(parts[0])) > 30) { return false; }
            return MessageDigest.isEqual(mac(key, purpose + "\n" + value + "\n" + parts[0]).getBytes(StandardCharsets.US_ASCII),
                    parts[1].getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException exception) { return false; }
    }

    public static String mac(String key, String value) {
        if (key == null || key.length() < 32) { throw new IllegalArgumentException("Service assertion keys must have at least 32 characters"); }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) { throw new IllegalStateException(exception); }
    }
}
