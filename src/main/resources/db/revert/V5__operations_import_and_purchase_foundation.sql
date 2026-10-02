-- Reviewed reversal for F-02 T09.
-- Stop if any document, purchase, revision, OCR usage or quota has been recorded.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM operations.import_document)
       OR EXISTS (SELECT 1 FROM operations.purchase)
       OR EXISTS (SELECT 1 FROM operations.purchase_item)
       OR EXISTS (SELECT 1 FROM operations.purchase_revision)
       OR EXISTS (SELECT 1 FROM operations.ocr_usage)
       OR EXISTS (SELECT 1 FROM platform.ocr_monthly_quota) THEN
        RAISE EXCEPTION 'Cannot reverse F-02 T09 while import, purchase or OCR data exists';
    END IF;
END
$$;

REVOKE SELECT, INSERT, UPDATE ON
    operations.import_document,
    operations.purchase,
    operations.purchase_item,
    operations.ocr_usage,
    platform.ocr_monthly_quota FROM app_runtime;
REVOKE SELECT, INSERT ON operations.purchase_revision FROM app_runtime;

DROP TABLE operations.ocr_usage;
DROP TABLE platform.ocr_monthly_quota;
DROP TABLE operations.purchase_revision;
DROP TABLE operations.purchase_item;
DROP TABLE operations.purchase;
DROP TABLE operations.import_document;
