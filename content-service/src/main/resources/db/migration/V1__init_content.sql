CREATE TABLE IF NOT EXISTS courses (
                                       id BIGSERIAL PRIMARY KEY,
                                       title VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );