package br.com.taas.saas.gestaoproducao.platform.access.security;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Adds the Supabase publishable key required by the project's JWKS endpoint. */
final class SupabaseJwkSetApiKeyInterceptor implements ClientHttpRequestInterceptor {

    private final String publishableKey;

    SupabaseJwkSetApiKeyInterceptor(String publishableKey) {
        this.publishableKey = publishableKey;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set("apikey", publishableKey);
        return execution.execute(request, body);
    }
}
