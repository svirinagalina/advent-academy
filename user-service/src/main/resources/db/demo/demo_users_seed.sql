-- Demo seed for the N+1 / overfetching evidence step (issue: fetch strategy).
-- Adds 20 users and assigns each of them to all 3 default roles
-- (ADMIN, INSTRUCTOR, STUDENT) so that GET /api/users triggers the full
-- user -> roles -> permissions graph.
--
-- Run against the running Postgres container, for example:
--   docker exec -i advent-postgres psql -U advent_user -d advent_academy \
--     < user-service/src/main/resources/db/demo/demo_users_seed.sql
--
-- Default roles + permissions are already inserted by Flyway migrations
-- V2-V4 (ADMIN/INSTRUCTOR/STUDENT roles, USER_MANAGE/COURSE_MANAGE/COURSE_READ
-- permissions, and role <-> permission grants).

-- Idempotent: only insert demo users that don't already exist.
INSERT INTO users (id, email, password_hash, first_name, last_name, status,
                   email_verified, mfa_enabled, failed_login_attempts,
                   created_at, updated_at)
SELECT
    gen_random_uuid(),
    'demo_user_' || g || '@example.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',  -- bcrypt of 'password'
    'First' || g,
    'Last'  || g,
    'ACTIVE',
    TRUE,
    FALSE,
    0,
    NOW(),
    NOW()
FROM generate_series(1, 20) AS g
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE email = 'demo_user_' || g || '@example.com'
);

-- Assign all 3 default roles to every demo user.
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.email LIKE 'demo_user_%@example.com'
  AND r.name IN ('ADMIN', 'INSTRUCTOR', 'STUDENT')
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
