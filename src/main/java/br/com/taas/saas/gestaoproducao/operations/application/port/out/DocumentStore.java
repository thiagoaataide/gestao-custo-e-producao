package br.com.taas.saas.gestaoproducao.operations.application.port.out;

public interface DocumentStore {
    void uploadNew(String opaqueKey, byte[] content, String contentType);
    byte[] download(String opaqueKey);
}
