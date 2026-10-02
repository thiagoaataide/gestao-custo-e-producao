package br.com.taas.saas.gestaoproducao.operations.application.importing;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.taas.saas.gestaoproducao.operations.application.port.out.DocumentStore;
import br.com.taas.saas.gestaoproducao.operations.application.port.out.DocumentStoreException;
import br.com.taas.saas.gestaoproducao.operations.application.port.out.ImportDocumentRepository;
import br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocument;
import br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocumentPurpose;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecision;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionResolver;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionType;
import br.com.taas.saas.gestaoproducao.platform.access.application.TenantAccessDeniedException;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.tenancy.application.TenantScopedTransactionExecutor;
import br.com.taas.saas.gestaoproducao.tenancy.model.TenantAccessContext;

@Service
public class ImportDocumentService {
    public static final int MAX_FILE_BYTES = ImportDocument.MAX_FILE_BYTES;
    private final AccessDecisionResolver accessDecisionResolver;
    private final TenantScopedTransactionExecutor transactionExecutor;
    private final ImportDocumentRepository documents;
    private final DocumentStore documentStore;

    public ImportDocumentService(AccessDecisionResolver accessDecisionResolver,
            TenantScopedTransactionExecutor transactionExecutor, ImportDocumentRepository documents,
            DocumentStore documentStore) {
        this.accessDecisionResolver = Objects.requireNonNull(accessDecisionResolver);
        this.transactionExecutor = Objects.requireNonNull(transactionExecutor);
        this.documents = Objects.requireNonNull(documents);
        this.documentStore = Objects.requireNonNull(documentStore);
    }

    public PreparedDocument prepare(ExternalSubject subject, ImportDocumentPurpose purpose, byte[] upload) {
        Objects.requireNonNull(purpose, "purpose must not be null");
        byte[] content = validateAndCopy(upload);
        String mimeType = detectMimeType(content);
        TenantAccessContext context = requireTenant(subject);
        UUID documentId = UUID.randomUUID();
        String opaqueKey = UUID.randomUUID() + "/" + UUID.randomUUID();
        ImportDocument document = ImportDocument.prepare(documentId, context.tenantId().value(), purpose,
                opaqueKey, context.identityId(), Instant.now());

        transactionExecutor.execute(context, () -> documents.save(document));
        try {
            documentStore.uploadNew(opaqueKey, content, mimeType);
        } catch (DocumentStoreException failure) {
            // Keep the PREPARING record for controlled retry/reconciliation; it cannot be read or confirmed.
            throw failure;
        }

        String hash = sha256(content);
        return transactionExecutor.execute(context, () -> {
            ImportDocument persisted = documents.findByTenantIdAndId(context.tenantId().value(), documentId)
                    .orElseThrow(DocumentNotFoundException::new);
            persisted.markReady(hash, mimeType, content.length, Instant.now());
            documents.save(persisted);
            return new PreparedDocument(documentId, purpose, mimeType, content.length, hash);
        });
    }

    public StoredDocument read(ExternalSubject subject, UUID documentId) {
        Objects.requireNonNull(documentId, "documentId must not be null");
        TenantAccessContext context = requireTenant(subject);
        DocumentMetadata metadata = transactionExecutor.execute(context, () -> {
            ImportDocument document = documents.findByTenantIdAndId(context.tenantId().value(), documentId)
                    .orElseThrow(DocumentNotFoundException::new);
            if (!document.canBeRead()) throw new DocumentNotFoundException();
            return new DocumentMetadata(document.storageKey(), document.sha256Hash(),
                    document.mimeType(), document.sizeBytes());
        });
        byte[] content = documentStore.download(metadata.storageKey());
        String actualMimeType = detectMimeType(content);
        if (content.length != metadata.sizeBytes()
                || !sha256(content).equals(metadata.sha256Hash())
                || !actualMimeType.equals(metadata.mimeType())) {
            throw new DocumentIntegrityException();
        }
        return new StoredDocument(actualMimeType, content);
    }

    private TenantAccessContext requireTenant(ExternalSubject subject) {
        AccessDecision decision = accessDecisionResolver.resolve(Objects.requireNonNull(subject));
        if (decision.type() != AccessDecisionType.TENANT_ACCESS) {
            throw new TenantAccessDeniedException(decision.type());
        }
        return decision.tenantContext();
    }

    private static byte[] validateAndCopy(byte[] upload) {
        Objects.requireNonNull(upload, "upload must not be null");
        if (upload.length == 0 || upload.length > MAX_FILE_BYTES) {
            throw new InvalidImportDocumentException();
        }
        return upload.clone();
    }

    private static String detectMimeType(byte[] content) {
        if (content == null) throw new DocumentIntegrityException();
        if (content.length >= 5 && content[0] == '%' && content[1] == 'P'
                && content[2] == 'D' && content[3] == 'F' && content[4] == '-') {
            return "application/pdf";
        }
        if (content.length >= 8 && (content[0] & 0xff) == 0x89 && content[1] == 'P'
                && content[2] == 'N' && content[3] == 'G' && (content[4] & 0xff) == 0x0d
                && (content[5] & 0xff) == 0x0a && (content[6] & 0xff) == 0x1a
                && (content[7] & 0xff) == 0x0a) {
            return "image/png";
        }
        if (content.length >= 3 && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        throw new InvalidImportDocumentException();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record DocumentMetadata(String storageKey, String sha256Hash, String mimeType, Long sizeBytes) { }

    public static final class InvalidImportDocumentException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public InvalidImportDocumentException() { super("Document must be a supported image or PDF under 6 MB"); }
    }
    public static final class DocumentNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public DocumentNotFoundException() { super("Document is unavailable"); }
    }
    public static final class DocumentIntegrityException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public DocumentIntegrityException() { super("Stored document failed integrity validation"); }
    }
}
