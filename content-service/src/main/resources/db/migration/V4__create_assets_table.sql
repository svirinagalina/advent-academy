CREATE TABLE assets
(
    id           UUID PRIMARY KEY,
    lesson_id    UUID         NOT NULL,
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size    BIGINT       NOT NULL,
    file_data    BYTEA        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_assets_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE
);

CREATE INDEX idx_assets_lesson_id ON assets (lesson_id);
