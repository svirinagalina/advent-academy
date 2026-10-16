CREATE TABLE lesson_assignments (
    lesson_id UUID PRIMARY KEY REFERENCES lessons(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE assignment_submissions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    course_id UUID NOT NULL,
    lesson_id UUID NOT NULL REFERENCES lesson_assignments(lesson_id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    attempt INT NOT NULL CHECK (attempt > 0),
    url VARCHAR(2048) NOT NULL,
    comment VARCHAR(4000) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('review', 'changes', 'accepted')),
    feedback VARCHAR(4000),
    reviewer_id UUID,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id, course_id) REFERENCES learning_enrollments(user_id, course_id) ON DELETE CASCADE,
    UNIQUE (user_id, course_id, lesson_id, request_id),
    UNIQUE (user_id, course_id, lesson_id, attempt),
    CHECK ((status = 'review' AND reviewer_id IS NULL AND reviewed_at IS NULL AND feedback IS NULL)
        OR (status <> 'review' AND reviewer_id IS NOT NULL AND reviewer_id <> user_id AND reviewed_at IS NOT NULL)),
    CHECK (status <> 'changes' OR length(trim(feedback)) > 0)
);
CREATE UNIQUE INDEX uq_assignment_pending ON assignment_submissions(user_id, course_id, lesson_id)
    WHERE status = 'review';
CREATE INDEX idx_assignment_review_queue ON assignment_submissions(status, submitted_at, id);
