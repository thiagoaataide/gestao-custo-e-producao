-- F-02 T04: tenant-scoped catalog and tenant operational time zone.
-- Existing tenant settings keep their data and receive the pilot time zone.

ALTER TABLE operations.tenant_settings
    ADD COLUMN operational_zone_id VARCHAR(64) NOT NULL DEFAULT 'America/Sao_Paulo',
    ADD CONSTRAINT tenant_settings_operational_zone_ck
        CHECK (btrim(operational_zone_id) <> '');

CREATE TABLE operations.ingredient (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    display_name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    quantity_dimension VARCHAR(16) NOT NULL,
    base_unit VARCHAR(8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ingredient_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id),
    CONSTRAINT ingredient_id_tenant_uq UNIQUE (id, tenant_id),
    CONSTRAINT ingredient_display_name_ck
        CHECK (btrim(display_name) <> ''),
    CONSTRAINT ingredient_normalized_name_ck
        CHECK (normalized_name = lower(btrim(normalized_name)) AND normalized_name <> ''),
    CONSTRAINT ingredient_unit_dimension_ck
        CHECK (
            (quantity_dimension = 'MASS' AND base_unit = 'g')
            OR (quantity_dimension = 'VOLUME' AND base_unit = 'ml')
            OR (quantity_dimension = 'COUNT' AND base_unit = 'un')
        )
);

CREATE UNIQUE INDEX ingredient_tenant_normalized_name_uq
    ON operations.ingredient (tenant_id, normalized_name);
CREATE INDEX ingredient_tenant_created_idx
    ON operations.ingredient (tenant_id, created_at, id);

CREATE TABLE operations.establishment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    display_name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT establishment_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id),
    CONSTRAINT establishment_id_tenant_uq UNIQUE (id, tenant_id),
    CONSTRAINT establishment_display_name_ck
        CHECK (btrim(display_name) <> ''),
    CONSTRAINT establishment_normalized_name_ck
        CHECK (normalized_name = lower(btrim(normalized_name)) AND normalized_name <> '')
);

CREATE UNIQUE INDEX establishment_tenant_normalized_name_uq
    ON operations.establishment (tenant_id, normalized_name);
CREATE INDEX establishment_tenant_created_idx
    ON operations.establishment (tenant_id, created_at, id);

ALTER TABLE operations.ingredient ENABLE ROW LEVEL SECURITY;
ALTER TABLE operations.ingredient FORCE ROW LEVEL SECURITY;
CREATE POLICY ingredient_select_policy ON operations.ingredient
    FOR SELECT TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY ingredient_insert_policy ON operations.ingredient
    FOR INSERT TO app_runtime
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY ingredient_update_policy ON operations.ingredient
    FOR UPDATE TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)))
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY ingredient_delete_policy ON operations.ingredient
    FOR DELETE TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));

ALTER TABLE operations.establishment ENABLE ROW LEVEL SECURITY;
ALTER TABLE operations.establishment FORCE ROW LEVEL SECURITY;
CREATE POLICY establishment_select_policy ON operations.establishment
    FOR SELECT TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY establishment_insert_policy ON operations.establishment
    FOR INSERT TO app_runtime
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY establishment_update_policy ON operations.establishment
    FOR UPDATE TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)))
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY establishment_delete_policy ON operations.establishment
    FOR DELETE TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));

REVOKE ALL ON TABLE operations.ingredient, operations.establishment FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON operations.ingredient, operations.establishment TO app_runtime;
