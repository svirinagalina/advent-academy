-- V5__create_audit_events_table.sql shipped empty in the audit-trail MR,
-- so AuditEventEntity has no backing table and Hibernate schema validation
-- fails on application startup. Add the table here as a follow-up.

CREATE TABLE IF NOT EXISTS audit_events (
    id              UUID         PRIMARY KEY,
    event_type      VARCHAR(50)  NOT NULL,
    actor_user_id   UUID,
    target_user_id  UUID,
    timestamp       TIMESTAMP    NOT NULL,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    result          VARCHAR(10)  NOT NULL,
    reason          VARCHAR(500),
    metadata        JSONB
);

CREATE INDEX IF NOT EXISTS idx_audit_events_timestamp     ON audit_events (timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_events_event_type    ON audit_events (event_type);
CREATE INDEX IF NOT EXISTS idx_audit_events_actor_user_id ON audit_events (actor_user_id);
