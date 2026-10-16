DO $$
BEGIN
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='academy_user') THEN
    REVOKE ALL PRIVILEGES ON TABLE flyway_schema_history FROM academy_user;
    REVOKE UPDATE,DELETE ON TABLE audit_events FROM academy_user;
    REVOKE UPDATE,DELETE ON TABLE user_admin_events FROM academy_user;
  END IF;
END $$;
