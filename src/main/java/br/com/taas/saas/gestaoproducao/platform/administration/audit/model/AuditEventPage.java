package br.com.taas.saas.gestaoproducao.platform.administration.audit.model;

import java.util.List;
import java.util.Objects;

public record AuditEventPage(
        List<AuditEvent> content,
        int page,
        int size,
        long totalElements) {

    public AuditEventPage {
        content = List.copyOf(Objects.requireNonNull(content, "content must not be null"));
        if (page < 0 || size < 1 || totalElements < 0) {
            throw new IllegalArgumentException("invalid audit page");
        }
    }

    public int totalPages() {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
