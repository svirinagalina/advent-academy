ALTER TABLE lessons ADD COLUMN required_assignment BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE learning_enrollments (
    user_id UUID NOT NULL,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, course_id)
);
CREATE INDEX idx_learning_enrollments_course ON learning_enrollments(course_id);

CREATE TABLE learning_completions (
    user_id UUID NOT NULL,
    course_id UUID NOT NULL,
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, course_id, lesson_id),
    FOREIGN KEY (user_id, course_id) REFERENCES learning_enrollments(user_id, course_id) ON DELETE CASCADE
);
