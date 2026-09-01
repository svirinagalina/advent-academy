package com.adventacademy.sharedresourceserver.adapter.exception;

public class JwtValidationException extends RuntimeException {

    public enum Reason {
        INVALID_SIGNATURE("Invalid JWT signature"),
        INVALID_ALGORITHM("Invalid algorithm (must be RS256)"),
        UNKNOWN_KID("Unknown key ID"),
        INVALID_ISSUER("Invalid issuer"),
        INVALID_AUDIENCE("Invalid audience"),
        INVALID_TOKEN_TYPE("Invalid token type (must be access)"),
        EXPIRED_TOKEN("Token has expired"),
        MALFORMED_TOKEN("Malformed JWT"),
        JWKS_UNAVAILABLE("JWKS endpoint unavailable"),
        MISSING_CLAIMS("Missing required claims"),
        OTHER("Validation failed");

        private final String description;

        Reason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private final Reason reason;

    public JwtValidationException(Reason reason) {
        super(reason.getDescription());
        this.reason = reason;
    }

    public JwtValidationException(Reason reason, String message) {
        super(reason.getDescription() + ": " + message);
        this.reason = reason;
    }

    public JwtValidationException(Reason reason, Throwable cause) {
        super(reason.getDescription(), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
