CREATE TABLE IF NOT EXISTS media_api_profiles (
    id text PRIMARY KEY,
    "baseUrl" text NOT NULL,
    "keyHash" text NOT NULL UNIQUE,
    "encryptedKey" text NOT NULL,
    "defaultModel" text,
    "createdAt" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" timestamp NOT NULL
);

CREATE TABLE IF NOT EXISTS media_api_sessions (
    id text PRIMARY KEY,
    "tokenHash" text NOT NULL UNIQUE,
    "profileId" text NOT NULL REFERENCES media_api_profiles(id) ON DELETE CASCADE,
    "expiresAt" timestamp NOT NULL,
    "createdAt" timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS media_api_sessions_profile_id_idx ON media_api_sessions("profileId");

INSERT INTO media_api_profiles (
    id, "baseUrl", "keyHash", "encryptedKey", "defaultModel", "createdAt", "updatedAt"
)
SELECT id, "baseUrl", "keyHash", "encryptedKey", "defaultModel", "createdAt", "updatedAt"
FROM api_profiles
ON CONFLICT (id) DO UPDATE SET
    "baseUrl" = EXCLUDED."baseUrl",
    "keyHash" = EXCLUDED."keyHash",
    "encryptedKey" = EXCLUDED."encryptedKey",
    "defaultModel" = EXCLUDED."defaultModel",
    "createdAt" = EXCLUDED."createdAt",
    "updatedAt" = EXCLUDED."updatedAt";

INSERT INTO media_api_sessions (
    id, "tokenHash", "profileId", "expiresAt", "createdAt"
)
SELECT session.id, session."tokenHash", session."profileId", session."expiresAt", session."createdAt"
FROM api_sessions session
JOIN media_api_profiles profile ON profile.id = session."profileId"
ON CONFLICT (id) DO UPDATE SET
    "tokenHash" = EXCLUDED."tokenHash",
    "profileId" = EXCLUDED."profileId",
    "expiresAt" = EXCLUDED."expiresAt",
    "createdAt" = EXCLUDED."createdAt";

DO $$
DECLARE
    source_count bigint;
    target_count bigint;
BEGIN
    SELECT COUNT(*) INTO source_count FROM api_profiles;
    SELECT COUNT(*) INTO target_count FROM media_api_profiles;
    IF source_count <> target_count THEN
        RAISE EXCEPTION 'media_api_profiles copy count mismatch: % <> %', source_count, target_count;
    END IF;

    SELECT COUNT(*) INTO source_count FROM api_sessions source
    WHERE EXISTS (SELECT 1 FROM media_api_profiles profile WHERE profile.id = source."profileId");
    SELECT COUNT(*) INTO target_count FROM media_api_sessions;
    IF source_count <> target_count THEN
        RAISE EXCEPTION 'media_api_sessions copy count mismatch: % <> %', source_count, target_count;
    END IF;

    IF EXISTS (
        SELECT 1 FROM api_profiles source
        LEFT JOIN media_api_profiles target ON target.id = source.id
        WHERE target.id IS NULL
    ) THEN
        RAISE EXCEPTION 'media_api_profiles primary key copy mismatch';
    END IF;

    IF EXISTS (
        SELECT 1 FROM media_api_sessions session
        LEFT JOIN media_api_profiles profile ON profile.id = session."profileId"
        WHERE profile.id IS NULL
    ) THEN
        RAISE EXCEPTION 'media_api_sessions contains an orphan profile reference';
    END IF;
END $$;

CREATE OR REPLACE FUNCTION media_sync_api_profile_to_media()
RETURNS trigger AS $$
BEGIN
    IF pg_trigger_depth() > 1 THEN
        RETURN COALESCE(NEW, OLD);
    END IF;
    IF TG_OP = 'DELETE' THEN
        DELETE FROM media_api_profiles WHERE id = OLD.id;
        RETURN OLD;
    END IF;
    INSERT INTO media_api_profiles (
        id, "baseUrl", "keyHash", "encryptedKey", "defaultModel", "createdAt", "updatedAt"
    )
    VALUES (
        NEW.id, NEW."baseUrl", NEW."keyHash", NEW."encryptedKey", NEW."defaultModel", NEW."createdAt", NEW."updatedAt"
    )
    ON CONFLICT (id) DO UPDATE SET
        "baseUrl" = EXCLUDED."baseUrl",
        "keyHash" = EXCLUDED."keyHash",
        "encryptedKey" = EXCLUDED."encryptedKey",
        "defaultModel" = EXCLUDED."defaultModel",
        "createdAt" = EXCLUDED."createdAt",
        "updatedAt" = EXCLUDED."updatedAt";
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION media_sync_api_profile_to_legacy()
RETURNS trigger AS $$
BEGIN
    IF pg_trigger_depth() > 1 THEN
        RETURN COALESCE(NEW, OLD);
    END IF;
    IF TG_OP = 'DELETE' THEN
        DELETE FROM api_profiles WHERE id = OLD.id;
        RETURN OLD;
    END IF;
    INSERT INTO api_profiles (
        id, "baseUrl", "keyHash", "encryptedKey", "defaultModel", "createdAt", "updatedAt"
    )
    VALUES (
        NEW.id, NEW."baseUrl", NEW."keyHash", NEW."encryptedKey", NEW."defaultModel", NEW."createdAt", NEW."updatedAt"
    )
    ON CONFLICT (id) DO UPDATE SET
        "baseUrl" = EXCLUDED."baseUrl",
        "keyHash" = EXCLUDED."keyHash",
        "encryptedKey" = EXCLUDED."encryptedKey",
        "defaultModel" = EXCLUDED."defaultModel",
        "createdAt" = EXCLUDED."createdAt",
        "updatedAt" = EXCLUDED."updatedAt";
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION media_sync_api_session_to_media()
RETURNS trigger AS $$
BEGIN
    IF pg_trigger_depth() > 1 THEN
        RETURN COALESCE(NEW, OLD);
    END IF;
    IF TG_OP = 'DELETE' THEN
        DELETE FROM media_api_sessions WHERE id = OLD.id;
        RETURN OLD;
    END IF;
    INSERT INTO media_api_sessions (
        id, "tokenHash", "profileId", "expiresAt", "createdAt"
    )
    VALUES (
        NEW.id, NEW."tokenHash", NEW."profileId", NEW."expiresAt", NEW."createdAt"
    )
    ON CONFLICT (id) DO UPDATE SET
        "tokenHash" = EXCLUDED."tokenHash",
        "profileId" = EXCLUDED."profileId",
        "expiresAt" = EXCLUDED."expiresAt",
        "createdAt" = EXCLUDED."createdAt";
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION media_sync_api_session_to_legacy()
RETURNS trigger AS $$
BEGIN
    IF pg_trigger_depth() > 1 THEN
        RETURN COALESCE(NEW, OLD);
    END IF;
    IF TG_OP = 'DELETE' THEN
        DELETE FROM api_sessions WHERE id = OLD.id;
        RETURN OLD;
    END IF;
    INSERT INTO api_sessions (
        id, "tokenHash", "profileId", "expiresAt", "createdAt"
    )
    VALUES (
        NEW.id, NEW."tokenHash", NEW."profileId", NEW."expiresAt", NEW."createdAt"
    )
    ON CONFLICT (id) DO UPDATE SET
        "tokenHash" = EXCLUDED."tokenHash",
        "profileId" = EXCLUDED."profileId",
        "expiresAt" = EXCLUDED."expiresAt",
        "createdAt" = EXCLUDED."createdAt";
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS api_profiles_sync_to_media ON api_profiles;
CREATE TRIGGER api_profiles_sync_to_media
AFTER INSERT OR UPDATE OR DELETE ON api_profiles
FOR EACH ROW EXECUTE FUNCTION media_sync_api_profile_to_media();

DROP TRIGGER IF EXISTS media_api_profiles_sync_to_legacy ON media_api_profiles;
CREATE TRIGGER media_api_profiles_sync_to_legacy
AFTER INSERT OR UPDATE OR DELETE ON media_api_profiles
FOR EACH ROW EXECUTE FUNCTION media_sync_api_profile_to_legacy();

DROP TRIGGER IF EXISTS api_sessions_sync_to_media ON api_sessions;
CREATE TRIGGER api_sessions_sync_to_media
AFTER INSERT OR UPDATE OR DELETE ON api_sessions
FOR EACH ROW EXECUTE FUNCTION media_sync_api_session_to_media();

DROP TRIGGER IF EXISTS media_api_sessions_sync_to_legacy ON media_api_sessions;
CREATE TRIGGER media_api_sessions_sync_to_legacy
AFTER INSERT OR UPDATE OR DELETE ON media_api_sessions
FOR EACH ROW EXECUTE FUNCTION media_sync_api_session_to_legacy();

COMMENT ON TABLE media_api_profiles IS 'Media direct API key profile';
COMMENT ON TABLE media_api_sessions IS 'Media API key web session';
COMMENT ON COLUMN media_api_profiles.id IS 'Profile ID';
COMMENT ON COLUMN media_api_profiles."baseUrl" IS 'Upstream base URL';
COMMENT ON COLUMN media_api_profiles."keyHash" IS 'API key SHA-256 hash';
COMMENT ON COLUMN media_api_profiles."encryptedKey" IS 'Stored API key value';
COMMENT ON COLUMN media_api_profiles."defaultModel" IS 'Default model key';
COMMENT ON COLUMN media_api_profiles."createdAt" IS 'Creation time';
COMMENT ON COLUMN media_api_profiles."updatedAt" IS 'Last update time';
COMMENT ON COLUMN media_api_sessions.id IS 'Session ID';
COMMENT ON COLUMN media_api_sessions."tokenHash" IS 'Session token SHA-256 hash';
COMMENT ON COLUMN media_api_sessions."profileId" IS 'Media API profile ID';
COMMENT ON COLUMN media_api_sessions."expiresAt" IS 'Session expiration time';
COMMENT ON COLUMN media_api_sessions."createdAt" IS 'Creation time';
