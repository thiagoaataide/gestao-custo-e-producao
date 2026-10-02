package br.com.taas.saas.gestaoproducao.operations.infrastructure.persistence.importing;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import br.com.taas.saas.gestaoproducao.operations.application.port.out.ImportDocumentRepository;
import br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocument;

@Repository
public class JpaImportDocumentRepository implements ImportDocumentRepository {
    private final ImportDocumentJpaRepository repository;

    public JpaImportDocumentRepository(ImportDocumentJpaRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public ImportDocument save(ImportDocument document) {
        return repository.saveAndFlush(Objects.requireNonNull(document));
    }

    @Override
    public Optional<ImportDocument> findByTenantIdAndId(UUID tenantId, UUID documentId) {
        return repository.findByTenantIdAndId(
                Objects.requireNonNull(tenantId), Objects.requireNonNull(documentId));
    }
}
