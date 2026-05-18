INSERT INTO roles (id, name, description, system_role, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN',      'Platform administrator', TRUE, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000002', 'INSTRUCTOR', 'Course instructor',      TRUE, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000003', 'STUDENT',    'Learner',                TRUE, NOW(), NOW())
    ON CONFLICT (name) DO NOTHING;