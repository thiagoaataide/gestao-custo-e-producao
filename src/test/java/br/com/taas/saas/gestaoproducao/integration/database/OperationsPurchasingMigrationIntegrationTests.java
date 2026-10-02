package br.com.taas.saas.gestaoproducao.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class OperationsPurchasingMigrationIntegrationTests {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ACTOR_A = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void rlsScopesDocumentsPurchasesItemsRevisionsAndOcrUsage() {
        UUID documentId = UUID.randomUUID();
        UUID establishmentId = createEstablishment(TENANT_A);
        UUID ingredientId = createIngredient(TENANT_A);
        UUID purchaseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID ocrUsageId = UUID.randomUUID();
        inTransaction(() -> {
            setTenant(TENANT_A);
            insertReadyDocument(TENANT_A, documentId, UUID.randomUUID().toString());
            insertPurchase(TENANT_A, purchaseId, documentId, establishmentId,
                    UUID.randomUUID().toString(), hash());
            insertPurchaseItem(TENANT_A, purchaseId, ingredientId, itemId);
            insertRevision(TENANT_A, purchaseId, null, revisionId);
            insertOcrUsage(TENANT_A, documentId, 1, ocrUsageId);
        });

        inTransaction(() -> {
            setTenant(TENANT_B);
            assertTenantInvisible("operations.import_document", documentId);
            assertTenantInvisible("operations.purchase", purchaseId);
            assertTenantInvisible("operations.purchase_item", itemId);
            assertTenantInvisible("operations.purchase_revision", revisionId);
            assertTenantInvisible("operations.ocr_usage", ocrUsageId);
            assertThat(jdbcTemplate.update("UPDATE operations.purchase SET status = 'CANCELLED' WHERE id = ?",
                    purchaseId)).isZero();
        });
        inTransaction(() -> {
            setTenant(null);
            assertTenantInvisible("operations.import_document", documentId);
            assertTenantInvisible("operations.purchase", purchaseId);
            assertTenantInvisible("operations.purchase_item", itemId);
            assertTenantInvisible("operations.purchase_revision", revisionId);
            assertTenantInvisible("operations.ocr_usage", ocrUsageId);
        });
    }

    @Test
    void compositeForeignKeysAndQuantityChecksRejectCrossTenantOrInvalidRows() {
        UUID documentB = UUID.randomUUID();
        UUID establishmentB = createEstablishment(TENANT_B);
        UUID ingredientA = createIngredient(TENANT_A);
        UUID purchaseB = UUID.randomUUID();
        inTransaction(() -> {
            setTenant(TENANT_B);
            insertReadyDocument(TENANT_B, documentB, UUID.randomUUID().toString());
            insertPurchase(TENANT_B, purchaseB, documentB, establishmentB,
                    UUID.randomUUID().toString(), hash());
        });
        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_B);
            insertPurchaseItem(TENANT_B, purchaseB, ingredientA, UUID.randomUUID());
        })).isInstanceOf(DataAccessException.class);
        UUID ingredientB = createIngredient(TENANT_B);
        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_B);
            insertInvalidQuantityItem(TENANT_B, purchaseB, ingredientB, UUID.randomUUID());
        })).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_B);
            jdbcTemplate.update("""
                    INSERT INTO operations.purchase_revision
                        (id, tenant_id, purchase_id, action, before_state, actor_identity_id)
                    VALUES (?, ?, ?, 'CORRECT', '[]'::jsonb, ?)
                    """, UUID.randomUUID(), TENANT_B, purchaseB, ACTOR_A);
        })).isInstanceOf(DataAccessException.class);
    }

    @Test
    void fiscalKeyAndDocumentHashStayReservedAfterCancellationAndAreTenantScoped() {
        String fiscalKey = "fiscal-" + UUID.randomUUID();
        String documentHash = hash();
        UUID establishmentA = createEstablishment(TENANT_A);
        UUID firstDocument = UUID.randomUUID();
        UUID firstPurchase = UUID.randomUUID();
        inTransaction(() -> {
            setTenant(TENANT_A);
            insertReadyDocument(TENANT_A, firstDocument, UUID.randomUUID().toString());
            insertPurchase(TENANT_A, firstPurchase, firstDocument, establishmentA,
                    fiscalKey, documentHash);
            jdbcTemplate.update("UPDATE operations.purchase SET status = 'CANCELLED' WHERE id = ?", firstPurchase);
        });

        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_A);
            UUID doc = UUID.randomUUID();
            insertReadyDocument(TENANT_A, doc, UUID.randomUUID().toString());
            insertPurchase(TENANT_A, UUID.randomUUID(), doc, establishmentA,
                    fiscalKey, hash());
        })).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_A);
            UUID doc = UUID.randomUUID();
            insertReadyDocument(TENANT_A, doc, UUID.randomUUID().toString());
            insertPurchase(TENANT_A, UUID.randomUUID(), doc, establishmentA,
                    "other-" + UUID.randomUUID(), documentHash);
        })).isInstanceOf(DataAccessException.class);

        UUID establishmentB = createEstablishment(TENANT_B);
        inTransaction(() -> {
            setTenant(TENANT_B);
            UUID doc = UUID.randomUUID();
            insertReadyDocument(TENANT_B, doc, UUID.randomUUID().toString());
            insertPurchase(TENANT_B, UUID.randomUUID(), doc, establishmentB,
                    fiscalKey, documentHash);
        });
    }

    @Test
    void ocrUsageIsIdempotentPerPageAndGlobalQuotaIsOnlyGrantedToRuntime() {
        UUID documentA = UUID.randomUUID();
        UUID pageOneUsage = UUID.randomUUID();
        UUID pageTwoUsage = UUID.randomUUID();
        inTransaction(() -> {
            setTenant(TENANT_A);
            insertReadyDocument(TENANT_A, documentA, UUID.randomUUID().toString());
            insertOcrUsage(TENANT_A, documentA, 1, pageOneUsage);
        });
        assertThatThrownBy(() -> inTransaction(() -> {
            setTenant(TENANT_A);
            insertOcrUsage(TENANT_A, documentA, 1, UUID.randomUUID());
        })).isInstanceOf(DataAccessException.class);
        inTransaction(() -> {
            setTenant(TENANT_A);
            insertOcrUsage(TENANT_A, documentA, 2, pageTwoUsage);
        });

        LocalDate month = LocalDate.of(2000, 1, 1)
                .plusMonths(Math.floorMod(UUID.randomUUID().hashCode(), 100_000));
        inTransaction(() -> {
            setTenant(TENANT_A);
            jdbcTemplate.update("""
                    INSERT INTO platform.ocr_monthly_quota (month_start, reserved_units, monthly_limit)
                    VALUES (?, 3, 900)
                    """, month);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT reserved_units FROM platform.ocr_monthly_quota WHERE month_start = ?",
                    Integer.class, month)).isEqualTo(3);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT has_column_privilege('app_runtime', 'platform.ocr_monthly_quota', 'reserved_units', 'UPDATE')",
                    Boolean.class)).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT has_column_privilege('app_runtime', 'platform.ocr_monthly_quota', 'monthly_limit', 'UPDATE')",
                    Boolean.class)).isFalse();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT has_table_privilege('app_runtime', 'platform.ocr_monthly_quota', 'DELETE')",
                    Boolean.class)).isFalse();
        });
        inTransaction(() -> {
            setTenant(TENANT_B);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT reserved_units FROM platform.ocr_monthly_quota WHERE month_start = ?",
                    Integer.class, month)).isEqualTo(3);
            assertTenantInvisible("operations.ocr_usage", pageOneUsage);
        });
    }

    @Test
    void purchaseRevisionsAndConfirmedSourcesCannotBeDeletedByRuntime() {
        UUID documentId = UUID.randomUUID();
        UUID establishmentId = createEstablishment(TENANT_A);
        UUID purchaseId = UUID.randomUUID();
        inTransaction(() -> {
            setTenant(TENANT_A);
            insertReadyDocument(TENANT_A, documentId, UUID.randomUUID().toString());
            insertPurchase(TENANT_A, purchaseId, documentId, establishmentId,
                    UUID.randomUUID().toString(), hash());
        UUID revisionId = UUID.randomUUID();
        insertRevision(TENANT_A, purchaseId, null, revisionId);
            assertThatThrownBy(() -> jdbcTemplate.update("""
                    UPDATE operations.import_document SET storage_key = storage_key || '/changed'
                     WHERE id = ?
                    """, documentId)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "DELETE FROM operations.purchase_revision WHERE id = ?", revisionId))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "DELETE FROM operations.purchase WHERE id = ?", purchaseId))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "DELETE FROM operations.import_document WHERE id = ?", documentId))
                    .isInstanceOf(DataAccessException.class);
        });
    }

    private UUID createEstablishment(UUID tenantId) {
        UUID id = UUID.randomUUID();
        inTransaction(() -> {
            setTenant(tenantId);
            String marker = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                    INSERT INTO operations.establishment (id, tenant_id, display_name, normalized_name)
                    VALUES (?, ?, ?, ?)
                    """, id, tenantId, "Store " + marker, "store " + marker);
        });
        return id;
    }

    private UUID createIngredient(UUID tenantId) {
        UUID id = UUID.randomUUID();
        inTransaction(() -> {
            setTenant(tenantId);
            String marker = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                    INSERT INTO operations.ingredient
                        (id, tenant_id, display_name, normalized_name, quantity_dimension, base_unit)
                    VALUES (?, ?, ?, ?, 'MASS', 'g')
                    """, id, tenantId, "Flour " + marker, "flour " + marker);
        });
        return id;
    }

    private void insertReadyDocument(UUID tenantId, UUID id, String storageKey) {
        String digest = hash();
        jdbcTemplate.update("""
                INSERT INTO operations.import_document
                    (id, tenant_id, purpose, storage_key, sha256_hash, mime_type, size_bytes,
                     status, created_by, uploaded_at)
                VALUES (?, ?, 'PURCHASE', ?, ?, 'application/pdf', 128, 'READY', ?, CURRENT_TIMESTAMP)
                """, id, tenantId, "opaque/" + tenantId + "/" + storageKey, digest, ACTOR_A);
    }

    private void insertPurchase(UUID tenantId, UUID id, UUID documentId, UUID establishmentId,
            String fiscalKey, String documentHash) {
        jdbcTemplate.update("""
                INSERT INTO operations.purchase
                    (id, tenant_id, document_id, purchase_date, establishment_id,
                     fiscal_access_key, document_sha256_hash, created_by)
                VALUES (?, ?, ?, CURRENT_DATE, ?, ?, ?, ?)
                """, id, tenantId, documentId, establishmentId, fiscalKey, documentHash, ACTOR_A);
    }

    private void insertPurchaseItem(UUID tenantId, UUID purchaseId, UUID ingredientId, UUID id) {
        jdbcTemplate.update("""
                INSERT INTO operations.purchase_item
                    (id, tenant_id, purchase_id, ingredient_id, source_line_number,
                     documented_description, documented_quantity, documented_unit,
                     selected_quantity, base_quantity, documented_amount,
                     documented_discount, net_amount)
                VALUES (?, ?, ?, ?, 1, 'Flour bag', 1, 'kg', 1, 1000, 12, 0, 12)
                """, id, tenantId, purchaseId, ingredientId);
    }

    private void insertInvalidQuantityItem(UUID tenantId, UUID purchaseId, UUID ingredientId, UUID id) {
        jdbcTemplate.update("""
                INSERT INTO operations.purchase_item
                    (id, tenant_id, purchase_id, ingredient_id, source_line_number,
                     documented_description, documented_quantity, documented_unit,
                     selected_quantity, base_quantity, documented_amount,
                     documented_discount, net_amount)
                VALUES (?, ?, ?, ?, 1, 'Invalid quantity', 1, 'kg', 2, 2000, 12, 0, 24)
                """, id, tenantId, purchaseId, ingredientId);
    }

    private void insertRevision(UUID tenantId, UUID purchaseId, UUID purchaseItemId, UUID id) {
        jdbcTemplate.update("""
                INSERT INTO operations.purchase_revision
                    (id, tenant_id, purchase_id, purchase_item_id, action,
                     before_state, after_state, actor_identity_id)
                VALUES (?, ?, ?, ?, 'CORRECT_PURCHASE', '{}'::jsonb, '{}'::jsonb, ?)
                """, id, tenantId, purchaseId, purchaseItemId, ACTOR_A);
    }

    private void insertOcrUsage(UUID tenantId, UUID documentId, int page, UUID id) {
        jdbcTemplate.update("""
                INSERT INTO operations.ocr_usage (id, tenant_id, document_id, page_number, month_start)
                VALUES (?, ?, ?, ?, DATE '2026-10-01')
                """, id, tenantId, documentId, page);
    }

    private String hash() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private void setTenant(UUID tenantId) {
        jdbcTemplate.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class,
                tenantId == null ? "" : tenantId.toString());
    }

    private void assertTenantInvisible(String table, UUID id) {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?",
                Long.class, id)).isZero();
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }
}
