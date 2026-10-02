package br.com.taas.saas.gestaoproducao.operations.application.importing;

import java.util.List;
import java.util.Objects;

public record PdfProcessingResult(List<PdfPageContent> pages) {
    public PdfProcessingResult {
        pages = List.copyOf(Objects.requireNonNull(pages));
    }

    public int pageCount() { return pages.size(); }
    public List<PdfPageContent> pagesRequiringOcr() {
        return pages.stream().filter(PdfPageContent::requiresOcr).toList();
    }
}
