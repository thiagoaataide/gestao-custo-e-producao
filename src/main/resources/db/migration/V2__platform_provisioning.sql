-- F-01 platform provisioning metadata and constraints.
-- Platform tables are administrative metadata. Operational tenant data remains
-- protected by the RLS policies created in V1.

ALTER TABLE platform.tenant
    ADD COLUMN name TEXT NOT NULL DEFAULT 'Tenant',
    ADD CONSTRAINT tenant_name_ck
        CHECK (btrim(name) <> '');

CREATE TABLE platform.platform_role_assignment (
    id UUID PRIMARY KEY,
    identity_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT platform_role_assignment_identity_fk
        FOREIGN KEY (identity_id) REFERENCES platform.external_identity (id),
    CONSTRAINT platform_role_assignment_role_ck
        CHECK (role IN ('PLATFORM_OWNER', 'PLATFORM_ADMIN')),
    CONSTRAINT platform_role_assignment_status_ck
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT platform_role_assignment_revoked_at_ck
        CHECK (
            (status = 'REVOKED' AND revoked_at IS NOT NULL)
            OR (status = 'ACTIVE' AND revoked_at IS NULL)
        )
);

CREATE UNIQUE INDEX platform_role_one_active_owner_uq
    ON platform.platform_role_assignment (role)
    WHERE status = 'ACTIVE' AND role = 'PLATFORM_OWNER';

CREATE UNIQUE INDEX platform_role_one_active_per_identity_uq
    ON platform.platform_role_assignment (identity_id, role)
    WHERE status = 'ACTIVE';

CREATE INDEX platform_role_identity_status_idx
    ON platform.platform_role_assignment (identity_id, status);

CREATE TABLE platform.invitation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    email TEXT NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'TENANT_USER',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    token_digest TEXT NOT NULL,
    identity_id UUID,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT invitation_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id),
    CONSTRAINT invitation_identity_fk
        FOREIGN KEY (identity_id) REFERENCES platform.external_identity (id),
    CONSTRAINT invitation_created_by_fk
        FOREIGN KEY (created_by) REFERENCES platform.external_identity (id),
    CONSTRAINT invitation_email_ck
        CHECK (email = lower(btrim(email)) AND btrim(email) <> ''),
    CONSTRAINT invitation_role_ck
        CHECK (role = 'TENANT_USER'),
    CONSTRAINT invitation_status_ck
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT invitation_token_digest_ck
        CHECK (btrim(token_digest) <> ''),
    CONSTRAINT invitation_expiry_ck
        CHECK (expires_at > created_at),
    CONSTRAINT invitation_transition_timestamps_ck
        CHECK (
            (status = 'PENDING' AND accepted_at IS NULL AND revoked_at IS NULL)
            OR (status = 'ACCEPTED' AND accepted_at IS NOT NULL AND revoked_at IS NULL)
            OR (status = 'REVOKED' AND accepted_at IS NULL AND revoked_at IS NOT NULL)
            OR (status = 'EXPIRED' AND accepted_at IS NULL AND revoked_at IS NULL)
        )
);

CREATE UNIQUE INDEX invitation_one_pending_tenant_email_uq
    ON platform.invitation (tenant_id, email)
    WHERE status = 'PENDING';

CREATE INDEX invitation_tenant_status_idx
    ON platform.invitation (tenant_id, status);

CREATE INDEX invitation_identity_status_idx
    ON platform.invitation (identity_id, status)
    WHERE identity_id IS NOT NULL;

CREATE INDEX invitation_created_by_idx
    ON platform.invitation (created_by);

CREATE INDEX invitation_pending_expiry_idx
    ON platform.invitation (expires_at)
    WHERE status = 'PENDING';

CREATE TABLE platform.audit_event (
    id UUID PRIMARY KEY,
    actor_identity_id UUID,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id UUID,
    result VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    CONSTRAINT audit_event_actor_identity_fk
        FOREIGN KEY (actor_identity_id) REFERENCES platform.external_identity (id),
    CONSTRAINT audit_event_action_ck
        CHECK (action IN (
            'BOOTSTRAP_OWNER',
            'TENANT_CREATED',
            'TENANT_SUSPENDED',
            'TENANT_REACTIVATED',
            'TENANT_CLOSED',
            'INVITATION_CREATED',
            'INVITATION_ACCEPTED',
            'INVITATION_REVOKED',
            'INVITATION_EXPIRED',
            'MEMBERSHIP_CREATED',
            'MEMBERSHIP_ACTIVATED',
            'MEMBERSHIP_REVOKED',
            'PLATFORM_ADMIN_GRANTED',
            'PLATFORM_ADMIN_REVOKED'
        )),
    CONSTRAINT audit_event_target_type_ck
        CHECK (target_type IN ('TENANT', 'INVITATION', 'MEMBERSHIP', 'PLATFORM_ROLE')),
    CONSTRAINT audit_event_result_ck
        CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED')),
    CONSTRAINT audit_event_metadata_object_ck
        CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX audit_event_actor_occurred_idx
    ON platform.audit_event (actor_identity_id, occurred_at DESC);

CREATE INDEX audit_event_target_occurred_idx
    ON platform.audit_event (target_type, target_id, occurred_at DESC);

CREATE INDEX audit_event_occurred_idx
    ON platform.audit_event (occurred_at DESC);

REVOKE ALL ON TABLE
    platform.platform_role_assignment,
    platform.invitation,
    platform.audit_event
FROM PUBLIC;

GRANT SELECT, INSERT, UPDATE ON
    platform.external_identity,
    platform.tenant,
    platform.membership,
    platform.platform_role_assignment,
    platform.invitation
TO app_runtime;

GRANT SELECT, INSERT ON platform.audit_event TO app_runtime;
