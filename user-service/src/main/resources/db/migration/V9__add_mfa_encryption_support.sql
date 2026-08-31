-- Add encrypted MFA secret support
-- mfa_secret remains for backward compatibility, mfa_secret_hash is the new encrypted field

ALTER TABLE users
    ADD COLUMN mfa_secret_hash VARCHAR(255),
    ADD COLUMN mfa_secret_created_at TIMESTAMP;

COMMENT ON COLUMN users.mfa_secret_hash IS 'Encrypted TOTP secret (AES-256-GCM). Format: version|encrypted_data|nonce. Only populated after MFA is confirmed.';
COMMENT ON COLUMN users.mfa_secret_created_at IS 'Timestamp when MFA secret was set/confirmed. Used for audit trail.';

-- Create recovery_codes table for one-time backup codes
CREATE TABLE recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_recovery_codes_user_id ON recovery_codes(user_id);
CREATE INDEX idx_recovery_codes_user_unused ON recovery_codes(user_id, used_at) WHERE used_at IS NULL;

COMMENT ON TABLE recovery_codes IS 'One-time backup codes for MFA re-enrollment. Hash is SHA-256 of 64-bit random hex string formatted as XXXX-XXXX.';
COMMENT ON COLUMN recovery_codes.code_hash IS 'SHA-256 digest of the recovery code. Never store plaintext.';
COMMENT ON COLUMN recovery_codes.used_at IS 'When the code was used (one-time use). NULL = unused.';
