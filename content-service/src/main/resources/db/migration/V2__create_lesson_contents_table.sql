CREATE TABLE lesson_contents
(
    id         UUID PRIMARY KEY,
    lesson_id  UUID        NOT NULL UNIQUE,
    markdown   TEXT        NOT NULL,
    html       TEXT,
    status     VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'REVIEW', 'PUBLISHED', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL

);