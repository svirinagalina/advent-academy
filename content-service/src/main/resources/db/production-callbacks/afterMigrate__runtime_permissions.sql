DO $$
BEGIN
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='academy_content') THEN
    REVOKE ALL PRIVILEGES ON TABLE flyway_schema_history_content FROM academy_content;
    REVOKE UPDATE,DELETE ON TABLE content_audit_events FROM academy_content;
  END IF;
END $$;
