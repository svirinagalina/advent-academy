INSERT INTO permissions (id, name, description, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000101', 'USER_MANAGE',   'Manage users',        NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000102', 'COURSE_MANAGE', 'Manage courses',      NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000103', 'COURSE_READ',   'Read course content', NOW(), NOW())
    ON CONFLICT (name) DO NOTHING;