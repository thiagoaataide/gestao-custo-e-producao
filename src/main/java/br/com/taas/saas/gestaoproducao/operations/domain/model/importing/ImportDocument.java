package br.com.taas.saas.gestaoproducao.operations.domain.model.importing;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "import_document", schema = "operations")
public class ImportDocument {
    public static final int MAX_FILE_BYTES = 6 * 1024 * 1024;

    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false, length = 16)
    private ImportDocumentPurpose purpose;
    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;
    @Column(name = "sha256_hash", length = 64)
    private String sha256Hash;
    @Column(name = "mime_type", length = 128)
    private String mimeType;
    @Column(name = "size_bytes")
    private Long sizeBytes;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ImportDocumentStatus status;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    protected ImportDocument() { }

    private ImportDocument(UUID id, UUID tenantId, ImportDocumentPurpose purpose,
            String storageKey, UUID createdBy, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.purpose = Objects.requireNonNull(purpose);
        this.storageKey = requireText(storageKey, "storageKey");
        this.createdBy = Objects.requireNonNull(createdBy);
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
        this.status = ImportDocumentStatus.PREPARING;
    }

    public static ImportDocument prepare(UUID id, UUID tenantId, ImportDocumentPurpose purpose,
            String storageKey, UUID createdBy, Instant now) {
        return new ImportDocument(id, tenantId, purpose, storageKey, createdBy, now);
    }

    public void markReady(String sha256Hash, String mimeType, long sizeBytes, Instant uploadedAt) {
        if (status != ImportDocumentStatus.PREPARING) {
            throw new IllegalStateException("Only a preparing document can become ready");
        }
        if (sha256Hash == null || !sha256Hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256Hash must be a lowercase SHA-256 value");
        }
        this.mimeType = requireText(mimeType, "mimeType");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        this.sha256Hash = sha256Hash;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = Objects.requireNonNull(uploadedAt);
        this.updatedAt = uploadedAt;
        this.status = ImportDocumentStatus.READY;
    }

    public boolean canBeRead() {
        return status == ImportDocumentStatus.READY || status == ImportDocumentStatus.CONFIRMED;
    }
    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public ImportDocumentPurpose purpose() { return purpose; }
    public String storageKey() { return storageKey; }
    public String sha256Hash() { return sha256Hash; }
    public String mimeType() { return mimeType; }
    public Long sizeBytes() { return sizeBytes; }
    public ImportDocumentStatus status() { return status; }
    public UUID createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant uploadedAt() { return uploadedAt; }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
