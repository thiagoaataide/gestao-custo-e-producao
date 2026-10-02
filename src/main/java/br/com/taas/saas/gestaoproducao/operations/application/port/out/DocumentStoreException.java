package br.com.taas.saas.gestaoproducao.operations.application.port.out;

public final class DocumentStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public DocumentStoreException() { super("Private document storage operation failed"); }
}
