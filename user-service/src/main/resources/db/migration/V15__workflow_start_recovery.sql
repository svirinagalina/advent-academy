ALTER TABLE role_requests ADD COLUMN workflow_next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE role_requests ADD COLUMN workflow_start_attempts INTEGER NOT NULL DEFAULT 0;
CREATE INDEX role_requests_start_queue ON role_requests(workflow_next_attempt_at,id)
    WHERE status='PENDING' AND process_instance_key IS NULL;
