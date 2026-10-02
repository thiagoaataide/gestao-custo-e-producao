package br.com.taas.saas.gestaoproducao.operations.application.port.out;

import java.util.Optional;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocument;

public interface ImportDocumentRepository {
    ImportDocument save(ImportDocument document);
    Optional<ImportDocument> findByTenantIdAndId(UUID tenantId, UUID documentId);
}
