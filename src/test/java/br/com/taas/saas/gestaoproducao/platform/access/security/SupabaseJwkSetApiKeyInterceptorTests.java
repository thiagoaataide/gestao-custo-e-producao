package br.com.taas.saas.gestaoproducao.platform.access.security;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class SupabaseJwkSetApiKeyInterceptorTests {

    @Test
    void sendsThePublishableKeyOnlyAsTheJwksApiKeyHeader() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new SupabaseJwkSetApiKeyInterceptor("test-publishable-key"));
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://project.supabase.co/auth/v1/.well-known/jwks.json"))
                .andExpect(header("apikey", "test-publishable-key"))
                .andRespond(withSuccess("{\"keys\":[]}", MediaType.APPLICATION_JSON));

        restTemplate.getForObject(
                "https://project.supabase.co/auth/v1/.well-known/jwks.json",
                String.class);

        server.verify();
    }
}
