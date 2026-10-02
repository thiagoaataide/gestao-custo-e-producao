package br.com.taas.saas.gestaoproducao.integration.operations.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import br.com.taas.saas.gestaoproducao.integration.operations.importing.ImportDocumentIntegrationTests.LocalDocumentStore;
import br.com.taas.saas.gestaoproducao.operations.application.importing.ImportDocumentService;
import br.com.taas.saas.gestaoproducao.operations.application.port.out.DocumentStore;
import br.com.taas.saas.gestaoproducao.operations.application.port.out.DocumentStoreException;
import br.com.taas.saas.gestaoproducao.operations.application.port.out.ImportDocumentRepository;
import br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocumentPurpose;
import br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocumentStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.tenancy.application.TenantScopedTransactionExecutor;

@SpringBootTest
@ActiveProfiles("test")
@Import(ImportDocumentIntegrationTests.LocalStorageConfiguration.class)
class ImportDocumentIntegrationTests {
    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ExternalSubject SUBJECT_A = ExternalSubject.fromSupabase("test-subject-a");
    private static final ExternalSubject SUBJECT_B = ExternalSubject.fromSupabase("test-subject-b");
    private static final byte[] PDF = "%PDF-1.7 integration".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Autowired private ImportDocumentService service;
    @Autowired private ImportDocumentRepository documents;
    @Autowired private TenantScopedTransactionExecutor transactions;
    @Autowired private LocalDocumentStore storage;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetStorage() { storage.reset(); }

    @Test
    void persistsReadyDocumentAndCanReadOriginalUnderTenantContext() {
        var created = service.prepare(SUBJECT_A, ImportDocumentPurpose.PURCHASE, PDF);

        var persisted = transactions.execute(SUBJECT_A, () ->
                documents.findByTenantIdAndId(TENANT_A, created.id()).orElseThrow());
        assertThat(persisted.status()).isEqualTo(ImportDocumentStatus.READY);
        assertThat(persisted.createdBy()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000101"));
        assertThat(service.read(SUBJECT_A, created.id()).content()).containsExactly(PDF);
        assertThat(storage.downloadCount()).isEqualTo(1);
    }

    @Test
    void differentTenantCannotReadDocumentOrCauseStorageDownload() {
        var created = service.prepare(SUBJECT_A, ImportDocumentPurpose.LIST, PDF);

        assertThatThrownBy(() -> service.read(SUBJECT_B, created.id()))
                .isInstanceOf(ImportDocumentService.DocumentNotFoundException.class);
        assertThat(storage.downloadCount()).isZero();
    }

    @Test
    void providerFailureLeavesPreparingRowThatCannotBeRead() {
        storage.setFailUploads(true);

        assertThatThrownBy(() -> service.prepare(SUBJECT_A, ImportDocumentPurpose.PURCHASE, PDF))
                .isInstanceOf(DocumentStoreException.class);
        UUID latestId = transactions.execute(SUBJECT_A, () -> jdbcTemplate.queryForObject("""
                SELECT id FROM operations.import_document
                WHERE tenant_id = ? AND purpose = 'PURCHASE' AND status = 'PREPARING'
                    AND size_bytes IS NULL
                ORDER BY created_at DESC, id DESC LIMIT 1
                """, UUID.class, TENANT_A));
        var preparing = transactions.execute(SUBJECT_A, () ->
                documents.findByTenantIdAndId(TENANT_A, latestId).orElseThrow());
        assertThat(preparing.status()).isEqualTo(ImportDocumentStatus.PREPARING);
        assertThat(preparing.canBeRead()).isFalse();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LocalStorageConfiguration {
        @Bean
        @Primary
        LocalDocumentStore localDocumentStore() { return new LocalDocumentStore(); }
    }

    static class LocalDocumentStore implements DocumentStore {
        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        private final AtomicInteger downloads = new AtomicInteger();
        private volatile boolean failUploads;

        @Override
        public void uploadNew(String opaqueKey, byte[] content, String contentType) {
            if (failUploads) throw new DocumentStoreException();
            if (objects.putIfAbsent(opaqueKey, content.clone()) != null) {
                throw new DocumentStoreException();
            }
        }

        @Override
        public byte[] download(String opaqueKey) {
            downloads.incrementAndGet();
            byte[] content = objects.get(opaqueKey);
            if (content == null) throw new DocumentStoreException();
            return content.clone();
        }

        int downloadCount() { return downloads.get(); }
        void setFailUploads(boolean failUploads) { this.failUploads = failUploads; }
        void reset() { objects.clear(); downloads.set(0); failUploads = false; }
    }
}
