CREATE TABLE IF NOT EXISTS courses (
                                       id          UUID         PRIMARY KEY,
                                       title       VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
    );c