-- Directions carry their creator so DirectionAssemblyService can authorize an edit against the
-- object and not only against the caller's role. Nullable on purpose: rows created before
-- ownership was introduced have no known owner and stay curatable by ADMIN and CONTENT_EDITOR.
ALTER TABLE content.directions
    ADD COLUMN IF NOT EXISTS owner_id UUID;

COMMENT ON COLUMN content.directions.owner_id IS 'User ID of the direction creator. Authorizes edits together with ADMIN and CONTENT_EDITOR.';

CREATE INDEX IF NOT EXISTS idx_directions_owner_id ON content.directions (owner_id);
CREATE INDEX IF NOT EXISTS idx_components_owner_id ON content.components (owner_id);
