ALTER TABLE content.courses
    ADD COLUMN owner_id UUID;

COMMENT ON COLUMN content.courses.owner_id IS 'User ID of the course creator/owner. Required for content-service resource-based authorization.';
