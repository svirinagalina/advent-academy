DROP TABLE IF EXISTS courses;

CREATE TABLE components (
                            id          UUID PRIMARY KEY,
                            title       VARCHAR(255) NOT NULL,
                            description TEXT,
                            status      VARCHAR(20)  NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
                            created_at  TIMESTAMPTZ  NOT NULL,
                            updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE lessons (
                         id           UUID PRIMARY KEY,
                         component_id UUID         NOT NULL REFERENCES components (id) ON DELETE CASCADE,
                         title        VARCHAR(255) NOT NULL,
                         position     INT          NOT NULL CHECK (position >= 0),
                         CONSTRAINT uq_lessons_component_position UNIQUE (component_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE courses (
                         id          UUID PRIMARY KEY,
                         title       VARCHAR(255) NOT NULL,
                         description TEXT,
                         status      VARCHAR(20)  NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
                         created_at  TIMESTAMPTZ  NOT NULL,
                         updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE course_components (
                                   course_id    UUID NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
                                   component_id UUID NOT NULL REFERENCES components (id),
                                   position     INT  NOT NULL CHECK (position >= 0),
                                   PRIMARY KEY (course_id, component_id),
                                   CONSTRAINT uq_course_components_position UNIQUE (course_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE directions (
                            id          UUID PRIMARY KEY,
                            title       VARCHAR(255) NOT NULL,
                            description TEXT,
                            created_at  TIMESTAMPTZ  NOT NULL,
                            updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE direction_courses (
                                   direction_id UUID NOT NULL REFERENCES directions (id) ON DELETE CASCADE,
                                   course_id    UUID NOT NULL REFERENCES courses (id),
                                   position     INT  NOT NULL CHECK (position >= 0),
                                   PRIMARY KEY (direction_id, course_id),
                                   CONSTRAINT uq_direction_courses_position UNIQUE (direction_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_lessons_component_id ON lessons (component_id);
CREATE INDEX idx_course_components_component_id ON course_components (component_id);
CREATE INDEX idx_direction_courses_course_id ON direction_courses (course_id);