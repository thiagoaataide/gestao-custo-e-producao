-- Reviewed reversal procedure for F-02 T01.
-- Stop if assignments or audit records exist; review and migrate their history
-- before manually resolving these guards and applying this reversal.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM platform.operational_manager_assignment) THEN
        RAISE EXCEPTION 'Cannot reverse operational manager assignments while assignment history exists';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM platform.audit_event
         WHERE action IN ('OPERATIONAL_MANAGER_ASSIGNED', 'OPERATIONAL_MANAGER_REVOKED')
    ) THEN
        RAISE EXCEPTION 'Cannot reverse operational manager audit actions while audit history exists';
    END IF;
END
$$;

REVOKE SELECT, INSERT, UPDATE
    ON platform.operational_manager_assignment
    FROM app_runtime;

DROP TABLE platform.operational_manager_assignment;
DROP FUNCTION platform.require_active_membership_for_operational_assignment();

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
            'PLATFORM_ADMIN_REVOKED'
        )),
    DROP CONSTRAINT audit_event_target_type_ck,
    ADD CONSTRAINT audit_event_target_type_ck
        CHECK (target_type IN ('TENANT', 'INVITATION', 'MEMBERSHIP', 'PLATFORM_ROLE'));

ALTER TABLE platform.membership
    DROP CONSTRAINT membership_id_tenant_uq;
