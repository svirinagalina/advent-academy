-- Course ownership backs the authorization rules in CourseAssemblyService, so no course may be
-- left without an owner. Rows created before V5 are assigned the reserved "system" owner: they
-- predate ownership and stay curatable by ADMIN and CONTENT_EDITOR only.
UPDATE courses
SET owner_id = '00000000-0000-0000-0000-000000000000'
WHERE owner_id IS NULL;

ALTER TABLE courses
    ALTER COLUMN owner_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_courses_owner_id ON courses (owner_id);

-- Assets record their uploader so that assets of unpublished lessons stay readable only by the
-- uploader and by curators. Nullable on purpose: existing assets have no known uploader.
ALTER TABLE assets
    ADD COLUMN owner_id UUID;

COMMENT ON COLUMN assets.owner_id IS 'User ID of the uploader. Authorizes downloads while the lesson content is not published.';

CREATE INDEX IF NOT EXISTS idx_assets_owner_id ON assets (owner_id);

-- Append-only audit trail of security-relevant operations; logs alone are not queryable evidence.
CREATE TABLE content_audit_events
(
    id          UUID PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL,
    actor_id    UUID,
    operation   VARCHAR(100) NOT NULL,
    target      VARCHAR(200),
    result      VARCHAR(20)  NOT NULL,
    reason      VARCHAR(500),
    occurred_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_content_audit_events_actor_id ON content_audit_events (actor_id);
CREATE INDEX idx_content_audit_events_occurred_at ON content_audit_events (occurred_at);
