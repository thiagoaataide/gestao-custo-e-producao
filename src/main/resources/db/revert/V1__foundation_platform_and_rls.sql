-- Reviewed reversal procedure for V1.
-- This is not Flyway Teams undo and must be executed only with an explicit
-- recovery decision. The schemas are dropped without CASCADE so the procedure
-- refuses to remove objects created by later migrations.

DROP TABLE IF EXISTS operations.tenant_settings;
DROP TABLE IF EXISTS platform.membership;
DROP TABLE IF EXISTS platform.tenant;
DROP TABLE IF EXISTS platform.external_identity;

DROP SCHEMA IF EXISTS operations;
DROP SCHEMA IF EXISTS platform;

-- Do not drop app_runtime: it is an environment credential and may predate V1.
