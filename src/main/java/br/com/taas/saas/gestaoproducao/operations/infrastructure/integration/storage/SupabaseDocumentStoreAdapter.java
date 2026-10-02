package br.com.taas.saas.gestaoproducao.operations.infrastructure.integration.storage;

import java.net.URI;
import java.io.IOException;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import br.com.taas.saas.gestaoproducao.operations.application.port.out.DocumentStore;
import br.com.taas.saas.gestaoproducao.operations.application.port.out.DocumentStoreException;
import br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocument;
import br.com.taas.saas.gestaoproducao.operations.infrastructure.config.SupabaseStorageProperties;

/** Backend-only Supabase Storage adapter; it never creates public or signed URLs. */
@Component
public final class SupabaseDocumentStoreAdapter implements DocumentStore {
    private final RestClient restClient;
    private final SupabaseStorageProperties properties;

    public SupabaseDocumentStoreAdapter(RestClient.Builder builder, SupabaseStorageProperties properties) {
        this.restClient = Objects.requireNonNull(builder).build();
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public void uploadNew(String opaqueKey, byte[] content, String contentType) {
        Objects.requireNonNull(content);
        requireKey(opaqueKey);
        requireConfigured();
        try {
            restClient.post()
                    .uri(objectUri("object", opaqueKey))
                    .header("apikey", properties.getSecretKey())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getSecretKey())
                    .header("x-upsert", "false")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException | IllegalArgumentException failure) {
            throw new DocumentStoreException();
        }
    }

    @Override
    public byte[] download(String opaqueKey) {
        requireKey(opaqueKey);
        requireConfigured();
        try {
            return restClient.get()
                    .uri(objectUri("object/authenticated", opaqueKey))
                    .header("apikey", properties.getSecretKey())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getSecretKey())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) throw new DocumentStoreException();
                        try {
                            byte[] content = response.getBody().readNBytes(ImportDocument.MAX_FILE_BYTES + 1);
                            if (content.length > ImportDocument.MAX_FILE_BYTES) throw new DocumentStoreException();
                            return content;
                        } catch (IOException readFailure) {
                            throw new DocumentStoreException();
                        }
                    });
        } catch (RestClientException failure) {
            throw new DocumentStoreException();
        }
    }

    private URI objectUri(String endpoint, String key) {
        String base = properties.getUrl().replaceAll("/+$", "");
        return URI.create(base + "/storage/v1/" + endpoint + "/"
                + properties.getBucket() + "/" + key);
    }

    private void requireConfigured() {
        if (!properties.configured() || !properties.getUrl().startsWith("https://")) {
            throw new DocumentStoreException();
        }
    }

    private static void requireKey(String key) {
        if (key == null || !key.matches("[0-9a-f-]{36}/[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("Storage key is invalid");
        }
    }

}
