-- The existing row remains the learner/publication snapshot. Never overwrite it on draft save.
CREATE TABLE lesson_drafts (
    lesson_id UUID PRIMARY KEY REFERENCES lessons(id) ON DELETE CASCADE,
    markdown TEXT NOT NULL,
    html TEXT NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Preserve access to historical attachments uploaded after a legacy publication.
-- From now on, uploads newer than the learner snapshot stay private until republished.
UPDATE lesson_contents c SET updated_at = GREATEST(c.updated_at, a.latest)
FROM (SELECT lesson_id, MAX(created_at) AS latest FROM assets GROUP BY lesson_id) a
WHERE c.lesson_id = a.lesson_id AND c.status = 'PUBLISHED';
