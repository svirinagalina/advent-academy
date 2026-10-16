ALTER TABLE refresh_tokens ADD COLUMN family_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE refresh_tokens ADD COLUMN consumed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);
COMMENT ON COLUMN refresh_tokens.consumed IS 'Retained until expiry to identify replay and revoke only the affected family';
