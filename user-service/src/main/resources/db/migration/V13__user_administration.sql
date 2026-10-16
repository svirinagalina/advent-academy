ALTER TABLE users ADD COLUMN admin_version BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX users_normalized_email ON users(lower(btrim(email)));

INSERT INTO roles(id,name,description,system_role,created_at,updated_at)
VALUES ('00000000-0000-0000-0000-000000000004','CONTENT_EDITOR','Content editor',true,now(),now())
ON CONFLICT(name) DO NOTHING;

CREATE TABLE admin_user_creations (
    actor_id UUID NOT NULL REFERENCES users(id), request_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id), fingerprint VARCHAR(64) NOT NULL,
    PRIMARY KEY(actor_id,request_id)
);
CREATE TABLE user_invitations (
    user_id UUID PRIMARY KEY REFERENCES users(id), generation UUID NOT NULL,
    request_id UUID NOT NULL, token_digest VARCHAR(64) NOT NULL UNIQUE,
    state VARCHAR(16) NOT NULL CHECK (state IN ('SENDING','SENT','FAILED','ACCEPTED')),
    expires_at TIMESTAMPTZ NOT NULL, attempted_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ, accepted_at TIMESTAMPTZ
);
CREATE TABLE user_invitation_requests (
    user_id UUID NOT NULL REFERENCES users(id), request_id UUID NOT NULL,
    PRIMARY KEY(user_id,request_id)
);
CREATE TABLE user_admin_events (
    id UUID PRIMARY KEY, actor_id UUID, actor_name VARCHAR(110) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id), action VARCHAR(32) NOT NULL,
    before_value TEXT NOT NULL, after_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX user_admin_events_history ON user_admin_events(user_id,created_at DESC,id DESC);

-- Legacy role endpoints/workflow and account-state changes also invalidate stale admin forms.
CREATE FUNCTION bump_user_admin_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_TABLE_NAME = 'user_roles' THEN
    UPDATE users SET admin_version=admin_version+1 WHERE id=COALESCE(NEW.user_id,OLD.user_id);
  ELSIF NEW.status IS DISTINCT FROM OLD.status OR NEW.email_verified IS DISTINCT FROM OLD.email_verified
        OR NEW.locked_until IS DISTINCT FROM OLD.locked_until THEN
    NEW.admin_version=OLD.admin_version+1;
  END IF;
  RETURN COALESCE(NEW,OLD);
END $$;
CREATE TRIGGER user_roles_admin_version AFTER INSERT OR DELETE ON user_roles
FOR EACH ROW EXECUTE FUNCTION bump_user_admin_version();
CREATE TRIGGER users_admin_version BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION bump_user_admin_version();
