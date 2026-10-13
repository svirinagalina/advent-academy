CREATE TABLE role_requests (
    id                 UUID         PRIMARY KEY,
    user_id            UUID         NOT NULL,
    requested_role_id  UUID         NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    reviewer_id        UUID,
    decision_reason    VARCHAR(500),
    created_at         TIMESTAMP    NOT NULL,
    reviewed_at        TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL,
    CONSTRAINT fk_role_requests_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_requests_requested_role
        FOREIGN KEY (requested_role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_requests_reviewer
        FOREIGN KEY (reviewer_id) REFERENCES users (id),
    CONSTRAINT chk_role_requests_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'FAILED'))
);

CREATE UNIQUE INDEX uq_role_requests_active_user_role
    ON role_requests (user_id, requested_role_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_role_requests_user_id ON role_requests (user_id);
CREATE INDEX idx_role_requests_status ON role_requests (status);
