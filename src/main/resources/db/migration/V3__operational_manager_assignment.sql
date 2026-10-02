-- F-02 T01: operational manager assignments remain platform metadata.

ALTER TABLE platform.membership
    ADD CONSTRAINT membership_id_tenant_uq UNIQUE (id, tenant_id);

CREATE TABLE platform.operational_manager_assignment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    assigned_by UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_by UUID,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT operational_manager_assignment_membership_fk
        FOREIGN KEY (membership_id, tenant_id)
        REFERENCES platform.membership (id, tenant_id),
    CONSTRAINT operational_manager_assignment_assigned_by_fk
        FOREIGN KEY (assigned_by) REFERENCES platform.external_identity (id),
    CONSTRAINT operational_manager_assignment_revoked_by_fk
        FOREIGN KEY (revoked_by) REFERENCES platform.external_identity (id),
    CONSTRAINT operational_manager_assignment_status_ck
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT operational_manager_assignment_revocation_ck
        CHECK (
            (status = 'ACTIVE' AND revoked_by IS NULL AND revoked_at IS NULL)
            OR (status = 'REVOKED' AND revoked_by IS NOT NULL AND revoked_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX operational_manager_one_active_per_membership_uq
    ON platform.operational_manager_assignment (membership_id)
    WHERE status = 'ACTIVE';

CREATE INDEX operational_manager_tenant_status_idx
    ON platform.operational_manager_assignment (tenant_id, status);

CREATE FUNCTION platform.require_active_membership_for_operational_assignment()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, platform
AS $$
DECLARE
    membership_status VARCHAR(16);
BEGIN
    IF NEW.status = 'ACTIVE' THEN
        SELECT membership.status
          INTO membership_status
          FROM platform.membership AS membership
         WHERE membership.id = NEW.membership_id
           AND membership.tenant_id = NEW.tenant_id
         FOR KEY SHARE;

        IF membership_status IS DISTINCT FROM 'ACTIVE' THEN
            RAISE EXCEPTION 'Operational manager assignment requires an active membership'
                USING ERRCODE = '23514',
                      CONSTRAINT = 'operational_manager_active_membership_ck';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION platform.require_active_membership_for_operational_assignment()
    FROM PUBLIC;

CREATE TRIGGER operational_manager_active_membership_trg
    BEFORE INSERT OR UPDATE OF tenant_id, membership_id, status
    ON platform.operational_manager_assignment
    FOR EACH ROW
    EXECUTE FUNCTION platform.require_active_membership_for_operational_assignment();

ALTER TABLE platform.audit_event
    DROP CONSTRAINT audit_event_action_ck,
    ADD CONSTRAINT audit_event_action_ck
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
            'PLATFORM_ADMIN_REVOKED',
            'OPERATIONAL_MANAGER_ASSIGNED',
            'OPERATIONAL_MANAGER_REVOKED'
        )),
    DROP CONSTRAINT audit_event_target_type_ck,
    ADD CONSTRAINT audit_event_target_type_ck
        CHECK (target_type IN (
            'TENANT', 'INVITATION', 'MEMBERSHIP', 'PLATFORM_ROLE',
            'OPERATIONAL_ASSIGNMENT'
        ));

REVOKE ALL ON TABLE platform.operational_manager_assignment FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE ON platform.operational_manager_assignment TO app_runtime;
