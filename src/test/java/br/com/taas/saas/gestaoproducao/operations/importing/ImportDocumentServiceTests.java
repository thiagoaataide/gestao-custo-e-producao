package br.com.taas.saas.gestaoproducao.operations.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.taas.saas.gestaoproducao.operations.application.importing.ImportDocumentService;
import br.com.taas.saas.gestaoproducao.operations.application.importing.ImportDocumentService.DocumentIntegrityException;
import br.com.taas.saas.gestaoproducao.operations.application.importing.ImportDocumentService.InvalidImportDocumentException;
import br.com.taas.saas.gestaoproducao.operations.application.port.out.DocumentStore;
import br.com.taas.saas.gestaoproducao.operations.application.port.out.DocumentStoreException;
import br.com.taas.saas.gestaoproducao.operations.application.port.out.ImportDocumentRepository;
import br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocument;
import br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocumentPurpose;
import br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocumentStatus;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecision;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionResolver;
import br.com.taas.saas.gestaoproducao.platform.access.application.TenantAccessDeniedException;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.tenancy.application.TenantScopedTransactionExecutor;
import br.com.taas.saas.gestaoproducao.tenancy.application.TenantUseCase;
import br.com.taas.saas.gestaoproducao.tenancy.model.TenantAccessContext;
import br.com.taas.saas.gestaoproducao.tenancy.model.TenantId;

@ExtendWith(MockitoExtension.class)
class ImportDocumentServiceTests {
    private static final ExternalSubject SUBJECT = ExternalSubject.fromSupabase("import-test-user");
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID IDENTITY_ID = UUID.randomUUID();
    private static final TenantAccessContext CONTEXT = new TenantAccessContext(
            SUBJECT, IDENTITY_ID, new TenantId(TENANT_ID), MembershipRole.TENANT_USER);
    private static final byte[] PDF = "%PDF-1.7 test document".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Mock private AccessDecisionResolver accessResolver;
    @Mock private TenantScopedTransactionExecutor transactionExecutor;
    @Mock private ImportDocumentRepository documents;
    @Mock private DocumentStore documentStore;

    private final Map<UUID, ImportDocument> stored = new HashMap<>();
    private ImportDocumentService service;

    @BeforeEach
    void setUp() {
        service = new ImportDocumentService(accessResolver, transactionExecutor, documents, documentStore);
        lenient().when(accessResolver.resolve(SUBJECT)).thenReturn(AccessDecision.tenantAccess(SUBJECT, CONTEXT));
        lenient().doAnswer(invocation -> {
            TenantUseCase<?> useCase = invocation.getArgument(1);
            return useCase.execute();
        }).when(transactionExecutor).execute(eq(CONTEXT), any(TenantUseCase.class));
        lenient().when(documents.save(any(ImportDocument.class))).thenAnswer(invocation -> {
            ImportDocument document = invocation.getArgument(0);
            stored.put(document.id(), document);
            return document;
        });
        lenient().when(documents.findByTenantIdAndId(eq(TENANT_ID), any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(stored.get(invocation.getArgument(1))));
    }

    @Test
    void uploadsDetectedPdfAndMarksDocumentReadyWithContentHash() {
        var result = service.prepare(SUBJECT, ImportDocumentPurpose.PURCHASE, PDF);

        assertThat(result.mimeType()).isEqualTo("application/pdf");
        assertThat(result.sizeBytes()).isEqualTo(PDF.length);
        assertThat(result.sha256Hash()).matches("[0-9a-f]{64}");
        assertThat(stored.get(result.id()).status()).isEqualTo(ImportDocumentStatus.READY);
        verify(documentStore).uploadNew(any(String.class), eq(PDF), eq("application/pdf"));
    }

    @Test
    void rejectsUnsupportedContentAndOversizedUploadBeforeCreatingDocument() {
        assertThatThrownBy(() -> service.prepare(SUBJECT, ImportDocumentPurpose.LIST,
                "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidImportDocumentException.class);
        byte[] oversized = Arrays.copyOf(PDF, ImportDocumentService.MAX_FILE_BYTES + 1);
        assertThatThrownBy(() -> service.prepare(SUBJECT, ImportDocumentPurpose.LIST, oversized))
                .isInstanceOf(InvalidImportDocumentException.class);
        assertThat(stored).isEmpty();
        verify(documentStore, never()).uploadNew(any(), any(), any());
    }

    @Test
    void failedProviderUploadLeavesOnlyUnreadablePreparingMetadata() {
        doThrow(new DocumentStoreException()).when(documentStore).uploadNew(any(), any(), any());

        assertThatThrownBy(() -> service.prepare(SUBJECT, ImportDocumentPurpose.PURCHASE, PDF))
                .isInstanceOf(RuntimeException.class);
        assertThat(stored).hasSize(1);
        assertThat(stored.values().iterator().next().status()).isEqualTo(ImportDocumentStatus.PREPARING);
        assertThat(stored.values().iterator().next().canBeRead()).isFalse();
    }

    @Test
    void deniedSubjectCannotCreateOrUploadDocument() {
        when(accessResolver.resolve(SUBJECT)).thenReturn(AccessDecision.notProvisioned(SUBJECT));

        assertThatThrownBy(() -> service.prepare(SUBJECT, ImportDocumentPurpose.LIST, PDF))
                .isInstanceOf(TenantAccessDeniedException.class);
        assertThat(stored).isEmpty();
        verify(documentStore, never()).uploadNew(any(), any(), any());
    }

    @Test
    void readsDocumentOnlyByTenantScopedIdAndVerifiesStoredBytes() {
        var prepared = service.prepare(SUBJECT, ImportDocumentPurpose.LIST, PDF);
        clearInvocations(documents);
        ImportDocument document = stored.get(prepared.id());
        when(documentStore.download(document.storageKey())).thenReturn(PDF);

        var read = service.read(SUBJECT, prepared.id());

        assertThat(read.mimeType()).isEqualTo("application/pdf");
        assertThat(read.content()).containsExactly(PDF);
        verify(documents).findByTenantIdAndId(TENANT_ID, prepared.id());
    }

    @Test
    void doesNotDownloadWhenTenantScopedDocumentLookupDoesNotFindId() {
        UUID foreignOrUnknownId = UUID.randomUUID();

        assertThatThrownBy(() -> service.read(SUBJECT, foreignOrUnknownId))
                .isInstanceOf(ImportDocumentService.DocumentNotFoundException.class);
        verify(documentStore, never()).download(any());
    }

    @Test
    void rejectsChangedObjectBytesEvenWhenStorageReturnsSuccessfully() {
        var prepared = service.prepare(SUBJECT, ImportDocumentPurpose.LIST, PDF);
        ImportDocument document = stored.get(prepared.id());
        when(documentStore.download(document.storageKey())).thenReturn("%PDF-tampered".getBytes());

        assertThatThrownBy(() -> service.read(SUBJECT, prepared.id()))
                .isInstanceOf(DocumentIntegrityException.class);
    }
}
