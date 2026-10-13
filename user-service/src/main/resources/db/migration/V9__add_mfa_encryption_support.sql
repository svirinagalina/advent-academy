-- MFA hardening (issue #45).
--
-- users.mfa_secret now holds the TOTP seed encrypted with AES-256-GCM under a versioned key:
--   <key version>:<base64 nonce>:<base64 ciphertext>
-- The plaintext seed never reaches the database. Seeds written by the previous implementation
-- were stored in clear; they cannot be encrypted by SQL, so the affected accounts are returned to
-- "MFA off" and have to enrol again. Rollback: the column keeps its name and type, older code
-- simply fails to validate the encrypted value and the same re-enrolment applies.
ALTER TABLE users
    ALTER COLUMN mfa_secret TYPE VARCHAR(512);

UPDATE users
SET mfa_secret  = NULL,
    mfa_enabled = FALSE
WHERE mfa_secret IS NOT NULL
  AND mfa_secret NOT LIKE 'v%:%:%';

-- Replay guard: the last TOTP time step accepted for the account. A code for that step or an
-- earlier one is refused, which works across replicas and needs no in-memory cache.
ALTER TABLE users
    ADD COLUMN mfa_last_used_step BIGINT;

COMMENT ON COLUMN users.mfa_secret IS 'TOTP secret encrypted with AES-256-GCM: <key version>:<nonce>:<ciphertext>. Never plaintext.';
COMMENT ON COLUMN users.mfa_last_used_step IS 'Last accepted TOTP time step; earlier or equal steps are replays.';

-- One-time recovery codes; only the SHA-256 digest is stored, and a code is burnt when used.
CREATE TABLE recovery_codes
(
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64)  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_recovery_codes_user_unused ON recovery_codes (user_id) WHERE used_at IS NULL;
CREATE UNIQUE INDEX uk_recovery_codes_user_hash ON recovery_codes (user_id, code_hash);

COMMENT ON TABLE recovery_codes IS 'One-time MFA recovery codes. Shown to the user once; only the SHA-256 digest is kept.';
COMMENT ON COLUMN recovery_codes.used_at IS 'When the code was burnt. NULL = still usable.';
