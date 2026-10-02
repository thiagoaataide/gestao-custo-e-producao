-- Reviewed reversal procedure for F-02 T04.
-- Stop if catalog data or a tenant-specific operational zone has been recorded.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM operations.ingredient)
       OR EXISTS (SELECT 1 FROM operations.establishment) THEN
        RAISE EXCEPTION 'Cannot reverse operations catalog while catalog data exists';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM operations.tenant_settings
         WHERE operational_zone_id <> 'America/Sao_Paulo'
    ) THEN
        RAISE EXCEPTION 'Cannot reverse operational zone while tenant overrides exist';
    END IF;
END
$$;

REVOKE SELECT, INSERT, UPDATE, DELETE
    ON operations.ingredient, operations.establishment FROM app_runtime;
DROP TABLE operations.ingredient;
DROP TABLE operations.establishment;
ALTER TABLE operations.tenant_settings
    DROP CONSTRAINT tenant_settings_operational_zone_ck,
    DROP COLUMN operational_zone_id;
