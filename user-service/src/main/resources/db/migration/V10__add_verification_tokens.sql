-- Verification / reset tokens (issue #45): random 32-byte tokens whose SHA-256 digest is the only
-- thing stored. Purpose separates email verification from password reset so one cannot be
-- replayed as the other; used_at makes a token single-use; expires_at bounds its life.
CREATE TABLE verification_tokens
(
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_digest VARCHAR(64) NOT NULL,
    purpose      VARCHAR(50) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   TIMESTAMPTZ   NOT NULL,
    used_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_verification_tokens_digest ON verification_tokens (token_digest);
-- At most one *active* token per user and purpose; used tokens stay for audit and never block a
-- re-issue (the service invalidates the previous one before creating a fresh token).
CREATE UNIQUE INDEX uk_verification_tokens_active ON verification_tokens (user_id, purpose) WHERE used_at IS NULL;

COMMENT ON TABLE verification_tokens IS 'One-time, expiring tokens for email verification and password reset. Digest only.';
COMMENT ON COLUMN verification_tokens.purpose IS 'email_verification or password_reset.';

-- The legacy column kept the verification token in clear text. Pending accounts get a fresh token
-- through POST /api/auth/resend-verification; nothing reads this column any more.
UPDATE users SET email_verification_token = NULL WHERE email_verification_token IS NOT NULL;
COMMENT ON COLUMN users.email_verification_token IS 'Deprecated since V10: tokens live in verification_tokens as digests.';
