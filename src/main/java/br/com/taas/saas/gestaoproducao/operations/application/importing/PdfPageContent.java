package br.com.taas.saas.gestaoproducao.operations.application.importing;

import java.util.Objects;

/** Extracted text is present for digital pages; PNG bytes are present only for OCR pages. */
public record PdfPageContent(int pageNumber, String extractedText, byte[] ocrImagePng) {
    public PdfPageContent {
        if (pageNumber < 1) throw new IllegalArgumentException("pageNumber must be positive");
        Objects.requireNonNull(extractedText);
        ocrImagePng = ocrImagePng == null ? null : ocrImagePng.clone();
    }

    @Override
    public byte[] ocrImagePng() {
        return ocrImagePng == null ? null : ocrImagePng.clone();
    }

    public boolean requiresOcr() { return ocrImagePng != null; }
}
