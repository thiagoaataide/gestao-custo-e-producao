package br.com.taas.saas.gestaoproducao.operations.application.importing;

import java.util.UUID;

import br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocumentPurpose;

public record PreparedDocument(UUID id, ImportDocumentPurpose purpose, String mimeType,
        long sizeBytes, String sha256Hash) { }
