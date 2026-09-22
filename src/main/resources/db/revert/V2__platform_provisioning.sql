-- Reviewed reversal procedure for V2.
-- This is not Flyway Teams undo. Execute it only after confirming that no
-- later migration depends on these objects.

REVOKE SELECT, INSERT, UPDATE ON
    platform.external_identity,
    platform.tenant,
    platform.membership
FROM app_runtime;

REVOKE SELECT, INSERT, UPDATE ON
    platform.platform_role_assignment,
    platform.invitation
FROM app_runtime;

REVOKE SELECT, INSERT ON platform.audit_event FROM app_runtime;

DROP TABLE platform.audit_event;
DROP TABLE platform.invitation;
DROP TABLE platform.platform_role_assignment;

ALTER TABLE platform.tenant
    DROP CONSTRAINT tenant_name_ck,
    DROP COLUMN name;

-- V1 grants SELECT on the original platform tables and remain responsible for
-- restoring those privileges after this reversal.
GRANT SELECT ON
    platform.external_identity,
    platform.tenant,
    platform.membership
TO app_runtime;
