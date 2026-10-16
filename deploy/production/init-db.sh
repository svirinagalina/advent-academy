#!/usr/bin/env sh
set -eu
export USER_RUNTIME_PASSWORD="$(cat /run/db-init/user-password)"
export CONTENT_RUNTIME_PASSWORD="$(cat /run/db-init/content-password)"
export MEETING_RUNTIME_PASSWORD="$(cat /run/db-init/meeting-password)"
export USER_MIGRATION_PASSWORD="$(cat /run/db-init/user-migration-password)"
export CONTENT_MIGRATION_PASSWORD="$(cat /run/db-init/content-migration-password)"
export MEETING_MIGRATION_PASSWORD="$(cat /run/db-init/meeting-migration-password)"
psql -v ON_ERROR_STOP=1 --username postgres --dbname academy <<'SQL'
\getenv user_pw USER_RUNTIME_PASSWORD
\getenv content_pw CONTENT_RUNTIME_PASSWORD
\getenv meeting_pw MEETING_RUNTIME_PASSWORD
\getenv user_migration_pw USER_MIGRATION_PASSWORD
\getenv content_migration_pw CONTENT_MIGRATION_PASSWORD
\getenv meeting_migration_pw MEETING_MIGRATION_PASSWORD
CREATE ROLE academy_user LOGIN PASSWORD :'user_pw';
CREATE ROLE academy_content LOGIN PASSWORD :'content_pw';
CREATE ROLE academy_meeting LOGIN PASSWORD :'meeting_pw';
CREATE ROLE academy_user_migrate LOGIN PASSWORD :'user_migration_pw';
CREATE ROLE academy_content_migrate LOGIN PASSWORD :'content_migration_pw';
CREATE ROLE academy_meeting_migrate LOGIN PASSWORD :'meeting_migration_pw';
CREATE SCHEMA identity AUTHORIZATION academy_user_migrate;
CREATE SCHEMA content AUTHORIZATION academy_content_migrate;
CREATE SCHEMA meeting AUTHORIZATION academy_meeting_migrate;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA identity TO academy_user;
GRANT USAGE ON SCHEMA content TO academy_content;
GRANT USAGE ON SCHEMA meeting TO academy_meeting;
ALTER DEFAULT PRIVILEGES FOR ROLE academy_user_migrate IN SCHEMA identity GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO academy_user;
ALTER DEFAULT PRIVILEGES FOR ROLE academy_content_migrate IN SCHEMA content GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO academy_content;
ALTER DEFAULT PRIVILEGES FOR ROLE academy_meeting_migrate IN SCHEMA meeting GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO academy_meeting;
ALTER DEFAULT PRIVILEGES FOR ROLE academy_user_migrate IN SCHEMA identity GRANT USAGE,SELECT ON SEQUENCES TO academy_user;
ALTER DEFAULT PRIVILEGES FOR ROLE academy_content_migrate IN SCHEMA content GRANT USAGE,SELECT ON SEQUENCES TO academy_content;
ALTER DEFAULT PRIVILEGES FOR ROLE academy_meeting_migrate IN SCHEMA meeting GRANT USAGE,SELECT ON SEQUENCES TO academy_meeting;
SQL
