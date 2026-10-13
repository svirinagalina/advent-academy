-- Existing process-local refresh tokens are deliberately invalidated by this upgrade.
-- Deploy all user-service replicas together; mixing old/new token stores is unsupported.
CREATE TABLE refresh_tokens
(
    jti_digest VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_tokens_user_expiry ON refresh_tokens (user_id, expires_at);
COMMENT ON TABLE refresh_tokens IS 'Shared single-use refresh token state. JTI digest only; password reset/change deletes all tokens for the account.';
