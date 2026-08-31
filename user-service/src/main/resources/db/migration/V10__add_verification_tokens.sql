-- Create verification tokens table for email verification and password reset flows
CREATE TABLE verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_digest VARCHAR(64) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    UNIQUE (user_id, purpose)
);

CREATE INDEX idx_verification_tokens_digest ON verification_tokens(token_digest);
CREATE INDEX idx_verification_tokens_user_purpose ON verification_tokens(user_id, purpose) WHERE used_at IS NULL;

COMMENT ON TABLE verification_tokens IS 'Secure, one-time, expiring tokens for email verification and password reset.';
COMMENT ON COLUMN verification_tokens.token_digest IS 'SHA-256 digest of random 32-byte token. Plaintext never stored.';
COMMENT ON COLUMN verification_tokens.purpose IS 'Token purpose: email_verification or password_reset. Prevents using wrong token type.';
COMMENT ON COLUMN verification_tokens.expires_at IS 'Token expiration time (24h for email, 1h for password reset).';
COMMENT ON COLUMN verification_tokens.used_at IS 'When token was consumed (one-time use). NULL = unused.';
