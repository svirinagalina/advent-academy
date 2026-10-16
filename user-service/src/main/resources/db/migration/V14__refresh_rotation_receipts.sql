CREATE TABLE refresh_rotation_receipts (
    token_digest VARCHAR(64) PRIMARY KEY,
    request_digest VARCHAR(64) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    response_ciphertext TEXT NOT NULL,
    replacement_digest VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX refresh_rotation_receipts_expiry ON refresh_rotation_receipts(expires_at);
