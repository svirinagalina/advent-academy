INSERT INTO permissions (id, name, description)
SELECT gen_random_uuid(), 'USER_READ', 'Read users'
    WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'USER_READ'
);

INSERT INTO permissions (id, name, description)
SELECT gen_random_uuid(), 'USER_CREATE', 'Create users'
    WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'USER_CREATE'
);

INSERT INTO permissions (id, name, description)
SELECT gen_random_uuid(), 'USER_UPDATE', 'Update users'
    WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'USER_UPDATE'
);

INSERT INTO permissions (id, name, description)
SELECT gen_random_uuid(), 'USER_DELETE', 'Delete users'
    WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'USER_DELETE'
);

INSERT INTO roles (id, name, description, system_role)
SELECT gen_random_uuid(), 'ADMIN', 'System administrator', true
    WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'ADMIN'
);

INSERT INTO roles (id, name, description, system_role)
SELECT gen_random_uuid(), 'USER', 'Default application user', true
    WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'USER'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.name IN ('USER_READ', 'USER_CREATE', 'USER_UPDATE', 'USER_DELETE')
WHERE r.name = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1
    FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.name = 'USER_READ'
WHERE r.name = 'USER'
  AND NOT EXISTS (
    SELECT 1
    FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
);