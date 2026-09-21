-- F-00 foundation: platform metadata, tenant-scoped anchor and RLS.
-- Flyway executes this file with the migration credential. The runtime role
-- must remain a separate, non-BYPASSRLS role.

CREATE SCHEMA IF NOT EXISTS platform;
CREATE SCHEMA IF NOT EXISTS operations;

-- The role is provisioned by the environment when possible. Creating a
-- passwordless NOLOGIN role here keeps an empty PostgreSQL database
-- executable without changing an existing managed-provider role.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_runtime') THEN
        CREATE ROLE app_runtime
            NOSUPERUSER
            NOCREATEDB
            NOCREATEROLE
            NOINHERIT
            NOREPLICATION
            NOBYPASSRLS;
    END IF;
END
$$;

CREATE TABLE platform.external_identity (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    external_subject VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT external_identity_provider_ck
        CHECK (provider IN ('SUPABASE')),
    CONSTRAINT external_identity_status_ck
        CHECK (status IN ('ACTIVE', 'BLOCKED')),
    CONSTRAINT external_identity_subject_ck
        CHECK (btrim(external_subject) <> ''),
    CONSTRAINT external_identity_provider_subject_uq
        UNIQUE (provider, external_subject)
);

CREATE TABLE platform.tenant (
    id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tenant_status_ck
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE TABLE platform.membership (
    id UUID PRIMARY KEY,
    identity_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT membership_identity_fk
        FOREIGN KEY (identity_id) REFERENCES platform.external_identity (id),
    CONSTRAINT membership_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id),
    CONSTRAINT membership_status_ck
        CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    CONSTRAINT membership_role_ck
        CHECK (btrim(role) <> ''),
    CONSTRAINT membership_revoked_at_ck
        CHECK (
            (status = 'REVOKED' AND revoked_at IS NOT NULL)
            OR (status <> 'REVOKED' AND revoked_at IS NULL)
        )
);

CREATE UNIQUE INDEX membership_one_active_per_identity_uq
    ON platform.membership (identity_id)
    WHERE status = 'ACTIVE';

CREATE INDEX membership_identity_status_idx
    ON platform.membership (identity_id, status);

CREATE INDEX membership_tenant_status_idx
    ON platform.membership (tenant_id, status);

-- This is a real tenant-scoped table used by F-00 to validate the RLS
-- boundary. Planning settings are added by a later operations migration.
CREATE TABLE operations.tenant_settings (
    tenant_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tenant_settings_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id)
);

ALTER TABLE operations.tenant_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE operations.tenant_settings FORCE ROW LEVEL SECURITY;

-- Missing or malformed tenant context does not match the UUID text and is
-- therefore denied without relying on a client-provided tenant_id.
CREATE POLICY tenant_settings_select_policy
    ON operations.tenant_settings
    FOR SELECT
    TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));

CREATE POLICY tenant_settings_insert_policy
    ON operations.tenant_settings
    FOR INSERT
    TO app_runtime
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));

CREATE POLICY tenant_settings_update_policy
    ON operations.tenant_settings
    FOR UPDATE
    TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)))
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));

CREATE POLICY tenant_settings_delete_policy
    ON operations.tenant_settings
    FOR DELETE
    TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));

REVOKE ALL ON SCHEMA platform, operations FROM PUBLIC;
GRANT USAGE ON SCHEMA platform, operations TO app_runtime;

REVOKE ALL ON ALL TABLES IN SCHEMA platform, operations FROM PUBLIC;
GRANT SELECT ON
    platform.external_identity,
    platform.tenant,
    platform.membership
TO app_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON operations.tenant_settings
TO app_runtime;
