package br.com.taas.saas.gestaoproducao.operations.application.importing;

public final class PdfProcessingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PdfProcessingException() {
        super("PDF could not be processed within the configured limits");
    }
}
