CREATE TABLE rsa_keys (
    id UUID PRIMARY KEY,
    kid VARCHAR(255) NOT NULL UNIQUE,
    algorithm VARCHAR(20) NOT NULL DEFAULT 'RS256',
    private_key_pem TEXT NOT NULL,
    public_key_pem TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    rotated_at TIMESTAMP,
    revoked_at TIMESTAMP,
    created_by VARCHAR(255)
);

CREATE INDEX idx_kid ON rsa_keys(kid);
CREATE INDEX idx_status ON rsa_keys(status);
