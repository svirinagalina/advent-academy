ALTER TABLE users ADD COLUMN authorization_version BIGINT NOT NULL DEFAULT 0;

-- Cover administrative, legacy RBAC and workflow writes in the same transaction.
-- These functions execute with the caller's privileges and resolve tables in the
-- schema that owns the trigger, including the production identity schema.
CREATE FUNCTION advance_user_authorization_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status
       OR NEW.email_verified IS DISTINCT FROM OLD.email_verified
       OR NEW.password_hash IS DISTINCT FROM OLD.password_hash THEN
        NEW.authorization_version := OLD.authorization_version + 1;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER users_authorization_version
BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION advance_user_authorization_version();

CREATE FUNCTION advance_role_authorization_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'UPDATE') THEN
        EXECUTE format('UPDATE %I.users SET authorization_version=authorization_version+1 WHERE id=$1', TG_TABLE_SCHEMA)
            USING OLD.user_id;
    END IF;
    IF TG_OP = 'INSERT' OR (TG_OP = 'UPDATE' AND NEW.user_id IS DISTINCT FROM OLD.user_id) THEN
        EXECUTE format('UPDATE %I.users SET authorization_version=authorization_version+1 WHERE id=$1', TG_TABLE_SCHEMA)
            USING NEW.user_id;
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER user_roles_authorization_version
AFTER INSERT OR UPDATE OR DELETE ON user_roles FOR EACH ROW EXECUTE FUNCTION advance_role_authorization_version();
