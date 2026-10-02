package br.com.taas.saas.gestaoproducao.operations.application.importing;

import java.util.Objects;

public record StoredDocument(String mimeType, byte[] content) {
    public StoredDocument {
        Objects.requireNonNull(mimeType);
        content = Objects.requireNonNull(content).clone();
    }
    @Override public byte[] content() { return content.clone(); }
}
