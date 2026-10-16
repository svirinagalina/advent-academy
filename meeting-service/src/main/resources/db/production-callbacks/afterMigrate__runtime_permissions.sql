DO $$
BEGIN
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='academy_meeting') THEN
    REVOKE ALL PRIVILEGES ON TABLE flyway_schema_history_meeting FROM academy_meeting;
  END IF;
END $$;
