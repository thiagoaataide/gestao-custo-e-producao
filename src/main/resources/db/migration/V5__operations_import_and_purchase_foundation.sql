-- F-02 T09: tenant-scoped source documents, purchase records and OCR quota.

CREATE TABLE operations.import_document (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    storage_key TEXT NOT NULL,
    sha256_hash VARCHAR(64),
    mime_type VARCHAR(128),
    size_bytes BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'PREPARING',
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_at TIMESTAMPTZ,
    CONSTRAINT import_document_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id),
    CONSTRAINT import_document_created_by_fk
        FOREIGN KEY (created_by) REFERENCES platform.external_identity (id),
    CONSTRAINT import_document_id_tenant_uq UNIQUE (id, tenant_id),
    CONSTRAINT import_document_purpose_ck
        CHECK (purpose IN ('LIST', 'PURCHASE')),
    CONSTRAINT import_document_storage_key_ck
        CHECK (btrim(storage_key) <> ''),
    CONSTRAINT import_document_sha256_ck
        CHECK (sha256_hash IS NULL OR sha256_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT import_document_size_ck
        CHECK (size_bytes IS NULL OR size_bytes > 0),
    CONSTRAINT import_document_status_ck
        CHECK (status IN ('PREPARING', 'READY', 'CONFIRMED', 'ABANDONED')),
    CONSTRAINT import_document_ready_metadata_ck
        CHECK (status NOT IN ('READY', 'CONFIRMED') OR
            (sha256_hash IS NOT NULL AND mime_type IS NOT NULL
             AND btrim(mime_type) <> '' AND size_bytes IS NOT NULL AND uploaded_at IS NOT NULL))
);

CREATE UNIQUE INDEX import_document_tenant_storage_key_uq
    ON operations.import_document (tenant_id, storage_key);
CREATE INDEX import_document_tenant_status_created_idx
    ON operations.import_document (tenant_id, status, created_at DESC);
CREATE INDEX import_document_tenant_hash_idx
    ON operations.import_document (tenant_id, sha256_hash)
    WHERE sha256_hash IS NOT NULL;

CREATE TABLE operations.purchase (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    document_id UUID NOT NULL,
    purchase_date DATE NOT NULL,
    establishment_id UUID NOT NULL,
    fiscal_access_key VARCHAR(64),
    document_sha256_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED',
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT purchase_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id),
    CONSTRAINT purchase_document_fk
        FOREIGN KEY (document_id, tenant_id)
        REFERENCES operations.import_document (id, tenant_id),
    CONSTRAINT purchase_establishment_fk
        FOREIGN KEY (establishment_id, tenant_id)
        REFERENCES operations.establishment (id, tenant_id),
    CONSTRAINT purchase_created_by_fk
        FOREIGN KEY (created_by) REFERENCES platform.external_identity (id),
    CONSTRAINT purchase_id_tenant_uq UNIQUE (id, tenant_id),
    CONSTRAINT purchase_document_tenant_uq UNIQUE (document_id, tenant_id),
    CONSTRAINT purchase_fiscal_key_ck
        CHECK (fiscal_access_key IS NULL OR btrim(fiscal_access_key) <> ''),
    CONSTRAINT purchase_document_sha256_ck
        CHECK (document_sha256_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT purchase_status_ck
        CHECK (status IN ('CONFIRMED', 'CANCELLED'))
);

-- Keep the key and content hash reserved after cancellation as well.
CREATE UNIQUE INDEX purchase_tenant_fiscal_key_uq
    ON operations.purchase (tenant_id, fiscal_access_key)
    WHERE fiscal_access_key IS NOT NULL;
CREATE UNIQUE INDEX purchase_tenant_document_hash_uq
    ON operations.purchase (tenant_id, document_sha256_hash);
CREATE INDEX purchase_tenant_date_idx
    ON operations.purchase (tenant_id, purchase_date DESC, id);

CREATE TABLE operations.purchase_item (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    purchase_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,
    source_line_number INTEGER NOT NULL,
    documented_description TEXT NOT NULL,
    documented_quantity NUMERIC(18, 6) NOT NULL,
    documented_unit VARCHAR(8) NOT NULL,
    selected_quantity NUMERIC(18, 6) NOT NULL,
    base_quantity NUMERIC(18, 6) NOT NULL,
    documented_amount NUMERIC(18, 4) NOT NULL,
    documented_discount NUMERIC(18, 4) NOT NULL DEFAULT 0,
    net_amount NUMERIC(18, 4) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT purchase_item_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id),
    CONSTRAINT purchase_item_purchase_fk
        FOREIGN KEY (purchase_id, tenant_id)
        REFERENCES operations.purchase (id, tenant_id),
    CONSTRAINT purchase_item_ingredient_fk
        FOREIGN KEY (ingredient_id, tenant_id)
        REFERENCES operations.ingredient (id, tenant_id),
    CONSTRAINT purchase_item_id_purchase_tenant_uq UNIQUE (id, purchase_id, tenant_id),
    CONSTRAINT purchase_item_source_line_ck CHECK (source_line_number > 0),
    CONSTRAINT purchase_item_description_ck CHECK (btrim(documented_description) <> ''),
    CONSTRAINT purchase_item_documented_quantity_ck CHECK (documented_quantity > 0),
    CONSTRAINT purchase_item_unit_ck CHECK (documented_unit IN ('g', 'kg', 'ml', 'l', 'un')),
    CONSTRAINT purchase_item_selected_quantity_ck
        CHECK (selected_quantity > 0 AND selected_quantity <= documented_quantity),
    CONSTRAINT purchase_item_base_quantity_ck CHECK (base_quantity > 0),
    CONSTRAINT purchase_item_amounts_ck
        CHECK (documented_amount >= 0 AND documented_discount >= 0
               AND documented_discount <= documented_amount AND net_amount >= 0),
    CONSTRAINT purchase_item_status_ck CHECK (status IN ('ACTIVE', 'CANCELLED'))
);

CREATE INDEX purchase_item_tenant_purchase_idx
    ON operations.purchase_item (tenant_id, purchase_id, status, source_line_number);
CREATE INDEX purchase_item_tenant_ingredient_idx
    ON operations.purchase_item (tenant_id, ingredient_id, purchase_id);

CREATE TABLE operations.purchase_revision (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    purchase_id UUID NOT NULL,
    purchase_item_id UUID,
    action VARCHAR(32) NOT NULL,
    before_state JSONB,
    after_state JSONB,
    reason TEXT,
    actor_identity_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT purchase_revision_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id),
    CONSTRAINT purchase_revision_purchase_fk
        FOREIGN KEY (purchase_id, tenant_id)
        REFERENCES operations.purchase (id, tenant_id),
    CONSTRAINT purchase_revision_item_fk
        FOREIGN KEY (purchase_item_id, purchase_id, tenant_id)
        REFERENCES operations.purchase_item (id, purchase_id, tenant_id),
    CONSTRAINT purchase_revision_actor_fk
        FOREIGN KEY (actor_identity_id) REFERENCES platform.external_identity (id),
    CONSTRAINT purchase_revision_action_ck CHECK (btrim(action) <> ''),
    CONSTRAINT purchase_revision_before_object_ck
        CHECK (before_state IS NULL OR jsonb_typeof(before_state) = 'object'),
    CONSTRAINT purchase_revision_after_object_ck
        CHECK (after_state IS NULL OR jsonb_typeof(after_state) = 'object'),
    CONSTRAINT purchase_revision_reason_ck
        CHECK (reason IS NULL OR btrim(reason) <> '')
);

CREATE INDEX purchase_revision_tenant_purchase_time_idx
    ON operations.purchase_revision (tenant_id, purchase_id, occurred_at DESC, id);

CREATE TABLE operations.ocr_usage (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    document_id UUID NOT NULL,
    page_number INTEGER NOT NULL,
    month_start DATE NOT NULL,
    reserved_units INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(24) NOT NULL DEFAULT 'RESERVED',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ocr_usage_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES platform.tenant (id),
    CONSTRAINT ocr_usage_document_fk
        FOREIGN KEY (document_id, tenant_id)
        REFERENCES operations.import_document (id, tenant_id),
    CONSTRAINT ocr_usage_month_ck
        CHECK (month_start = date_trunc('month', month_start)::DATE),
    CONSTRAINT ocr_usage_page_ck CHECK (page_number > 0),
    CONSTRAINT ocr_usage_units_ck CHECK (reserved_units > 0),
    CONSTRAINT ocr_usage_status_ck
        CHECK (status IN ('RESERVED', 'COMPLETED', 'UNCERTAIN', 'RELEASED')),
    CONSTRAINT ocr_usage_tenant_document_page_uq
        UNIQUE (tenant_id, document_id, page_number)
);

CREATE INDEX ocr_usage_tenant_month_status_idx
    ON operations.ocr_usage (tenant_id, month_start, status);

CREATE TABLE platform.ocr_monthly_quota (
    month_start DATE PRIMARY KEY,
    reserved_units INTEGER NOT NULL DEFAULT 0,
    monthly_limit INTEGER NOT NULL DEFAULT 900,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ocr_monthly_quota_month_ck
        CHECK (month_start = date_trunc('month', month_start)::DATE),
    CONSTRAINT ocr_monthly_quota_reserved_ck CHECK (reserved_units >= 0),
    CONSTRAINT ocr_monthly_quota_limit_ck CHECK (monthly_limit > 0)
);

ALTER TABLE operations.import_document ENABLE ROW LEVEL SECURITY;
ALTER TABLE operations.import_document FORCE ROW LEVEL SECURITY;
CREATE POLICY import_document_select_policy ON operations.import_document
    FOR SELECT TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY import_document_insert_policy ON operations.import_document
    FOR INSERT TO app_runtime
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY import_document_update_policy ON operations.import_document
    FOR UPDATE TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)))
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));

ALTER TABLE operations.purchase ENABLE ROW LEVEL SECURITY;
ALTER TABLE operations.purchase FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_select_policy ON operations.purchase
    FOR SELECT TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY purchase_insert_policy ON operations.purchase
    FOR INSERT TO app_runtime
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY purchase_update_policy ON operations.purchase
    FOR UPDATE TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)))
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));

ALTER TABLE operations.purchase_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE operations.purchase_item FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_item_select_policy ON operations.purchase_item
    FOR SELECT TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY purchase_item_insert_policy ON operations.purchase_item
    FOR INSERT TO app_runtime
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY purchase_item_update_policy ON operations.purchase_item
    FOR UPDATE TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)))
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));

ALTER TABLE operations.purchase_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE operations.purchase_revision FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_revision_select_policy ON operations.purchase_revision
    FOR SELECT TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY purchase_revision_insert_policy ON operations.purchase_revision
    FOR INSERT TO app_runtime
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));

ALTER TABLE operations.ocr_usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE operations.ocr_usage FORCE ROW LEVEL SECURITY;
CREATE POLICY ocr_usage_select_policy ON operations.ocr_usage
    FOR SELECT TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY ocr_usage_insert_policy ON operations.ocr_usage
    FOR INSERT TO app_runtime
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));
CREATE POLICY ocr_usage_update_policy ON operations.ocr_usage
    FOR UPDATE TO app_runtime
    USING (tenant_id::TEXT = (select current_setting('app.tenant_id', true)))
    WITH CHECK (tenant_id::TEXT = (select current_setting('app.tenant_id', true)));

REVOKE ALL ON TABLE
    operations.import_document,
    operations.purchase,
    operations.purchase_item,
    operations.purchase_revision,
    operations.ocr_usage,
    platform.ocr_monthly_quota
FROM PUBLIC;
GRANT SELECT, INSERT ON
    operations.import_document,
    operations.purchase,
    operations.purchase_item,
    operations.ocr_usage
TO app_runtime;
GRANT UPDATE (sha256_hash, mime_type, size_bytes, status, updated_at, uploaded_at)
    ON operations.import_document TO app_runtime;
GRANT UPDATE (purchase_date, establishment_id, fiscal_access_key, status)
    ON operations.purchase TO app_runtime;
GRANT UPDATE (ingredient_id, source_line_number, documented_description,
    documented_quantity, documented_unit, selected_quantity, base_quantity,
    documented_amount, documented_discount, net_amount, status)
    ON operations.purchase_item TO app_runtime;
GRANT UPDATE (reserved_units, status, updated_at)
    ON operations.ocr_usage TO app_runtime;
GRANT SELECT, INSERT ON operations.purchase_revision TO app_runtime;
GRANT SELECT, INSERT ON platform.ocr_monthly_quota TO app_runtime;
GRANT UPDATE (reserved_units, updated_at) ON platform.ocr_monthly_quota TO app_runtime;
