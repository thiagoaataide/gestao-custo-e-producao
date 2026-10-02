package br.com.taas.saas.gestaoproducao.operations.application.port.out;

import br.com.taas.saas.gestaoproducao.operations.application.importing.PdfProcessingResult;

public interface PdfDocumentProcessor {
    int MAX_PAGES = 5;
    int RENDER_DPI = 150;
    long MAX_PAGE_PIXELS = 8_000_000L;
    int MAX_RENDERED_BYTES = 12 * 1024 * 1024;
    int MAX_EXTRACTED_CHARS_PER_PAGE = 100_000;

    PdfProcessingResult process(byte[] originalPdf);
}
