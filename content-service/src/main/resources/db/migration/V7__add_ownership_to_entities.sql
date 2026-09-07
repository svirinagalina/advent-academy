ALTER TABLE content.courses
    ALTER COLUMN owner_id SET NOT NULL;

COMMENT ON COLUMN content.courses.owner_id IS 'User ID of the course creator/owner. Required for content-service resource-based authorization.';

ALTER TABLE content.components
    ADD COLUMN IF NOT EXISTS owner_id UUID;

COMMENT ON COLUMN content.components.owner_id IS 'User ID of the component creator. Inherited from parent course if not set explicitly.';

ALTER TABLE content.lessons
    ADD COLUMN IF NOT EXISTS owner_id UUID;

COMMENT ON COLUMN content.lessons.owner_id IS 'User ID of the lesson creator. Inherited from parent component/course if not set explicitly.';

ALTER TABLE content.assets
    ADD COLUMN IF NOT EXISTS owner_id UUID;

COMMENT ON COLUMN content.assets.owner_id IS 'User ID of the asset uploader. Inherited from parent lesson if not set explicitly.';
