CREATE TABLE users (
    id                         UUID         PRIMARY KEY,
    email                      VARCHAR(255) NOT NULL UNIQUE,
    password_hash              VARCHAR(255) NOT NULL,
    first_name                 VARCHAR(50)  NOT NULL,
    last_name                  VARCHAR(50)  NOT NULL,
    status                     VARCHAR(20)  NOT NULL,
    email_verified             BOOLEAN      NOT NULL,
    email_verification_token   VARCHAR(255),
    mfa_enabled                BOOLEAN      NOT NULL,
    mfa_secret                 VARCHAR(255),
    failed_login_attempts      INTEGER      NOT NULL,
    locked_until               TIMESTAMP,
    last_login_at              TIMESTAMP,
    created_at                 TIMESTAMP    NOT NULL,
    updated_at                 TIMESTAMP    NOT NULL
);

CREATE TABLE roles (
    id           UUID         PRIMARY KEY,
    name         VARCHAR(50)  NOT NULL UNIQUE,
    description  VARCHAR(255),
    system_role  BOOLEAN      NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);

CREATE TABLE permissions (
    id           UUID         PRIMARY KEY,
    name         VARCHAR(50)  NOT NULL UNIQUE,
    description  VARCHAR(255),
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);

CREATE TABLE user_roles (
    user_id  UUID NOT NULL,
    role_id  UUID NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE TABLE role_permissions (
    role_id        UUID NOT NULL,
    permission_id  UUID NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions (permission_id);
