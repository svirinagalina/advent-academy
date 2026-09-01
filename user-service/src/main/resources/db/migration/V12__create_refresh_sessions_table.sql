CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_family_id UUID NOT NULL,
    jti_digest VARCHAR(64) NOT NULL UNIQUE,
    device_id VARCHAR(255),
    device_name VARCHAR(500),
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL,
    rotated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    last_activity_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX idx_user_id_status ON refresh_sessions(user_id, status);
CREATE INDEX idx_jti_digest ON refresh_sessions(jti_digest);
CREATE INDEX idx_token_family_id ON refresh_sessions(token_family_id);
CREATE INDEX idx_expires_at ON refresh_sessions(expires_at);
