package br.com.taas.saas.gestaoproducao.operations.infrastructure.persistence.importing;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocument;

public interface ImportDocumentJpaRepository extends JpaRepository<ImportDocument, UUID> {
    Optional<ImportDocument> findByTenantIdAndId(UUID tenantId, UUID id);
}
