package br.com.taas.saas.gestaoproducao.operations.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.taas.saas.gestaoproducao.operations.application.port.out.DocumentStoreException;
import br.com.taas.saas.gestaoproducao.operations.infrastructure.config.SupabaseStorageProperties;
import br.com.taas.saas.gestaoproducao.operations.infrastructure.integration.storage.SupabaseDocumentStoreAdapter;

class SupabaseDocumentStoreAdapterTests {
    private static final String BASE = "https://storage-project.supabase.co";
    private static final String BUCKET = "private-import-documents";
    private static final String SECRET = "test-secret-key";
    private static final String KEY = "11111111-1111-1111-1111-111111111111/"
            + "22222222-2222-2222-2222-222222222222";
    private static final byte[] PDF = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private MockRestServiceServer server;
    private SupabaseDocumentStoreAdapter store;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        SupabaseStorageProperties properties = new SupabaseStorageProperties();
        properties.setUrl(BASE);
        properties.setBucket(BUCKET);
        properties.setSecretKey(SECRET);
        store = new SupabaseDocumentStoreAdapter(builder, properties);
    }

    @Test
    void uploadsToOpaquePrivateObjectPathWithoutOverwrite() {
        server.expect(requestTo(URI.create(BASE + "/storage/v1/object/" + BUCKET + "/" + KEY)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", SECRET))
                .andExpect(header("Authorization", "Bearer " + SECRET))
                .andExpect(header("x-upsert", "false"))
                .andExpect(header("Cache-Control", "no-store"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(PDF))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        store.uploadNew(KEY, PDF, "application/pdf");

        server.verify();
    }

    @Test
    void downloadsFromAuthenticatedPrivateObjectEndpoint() {
        server.expect(requestTo(URI.create(BASE + "/storage/v1/object/authenticated/" + BUCKET + "/" + KEY)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", SECRET))
                .andExpect(header("Authorization", "Bearer " + SECRET))
                .andExpect(header("Cache-Control", "no-store"))
                .andRespond(withSuccess(PDF, MediaType.APPLICATION_PDF));

        assertThat(store.download(KEY)).containsExactly(PDF);

        server.verify();
    }

    @Test
    void rejectsStorageFailureWithoutExposingProviderResponse() {
        server.expect(requestTo(URI.create(BASE + "/storage/v1/object/" + BUCKET + "/" + KEY)))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("provider diagnostic containing sensitive details"));

        assertThatThrownBy(() -> store.uploadNew(KEY, PDF, "application/pdf"))
                .isInstanceOf(DocumentStoreException.class)
                .hasMessage("Private document storage operation failed")
                .hasNoCause();

        server.verify();
    }

    @Test
    void rejectsClientProvidedNonOpaqueStoragePathBeforeHttpCall() {
        assertThatThrownBy(() -> store.download("tenant/../../other.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void boundsDownloadedBytesBeforeBufferingAnOversizedObject() {
        byte[] oversized = Arrays.copyOf(PDF,
                br.com.taas.saas.gestaoproducao.operations.domain.model.importing.ImportDocument.MAX_FILE_BYTES + 1);
        server.expect(requestTo(URI.create(BASE + "/storage/v1/object/authenticated/" + BUCKET + "/" + KEY)))
                .andRespond(withSuccess(oversized, MediaType.APPLICATION_PDF));

        assertThatThrownBy(() -> store.download(KEY))
                .isInstanceOf(DocumentStoreException.class)
                .hasMessage("Private document storage operation failed");

        server.verify();
    }
}
