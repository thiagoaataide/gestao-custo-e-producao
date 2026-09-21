package br.com.taas.saas.gestaoproducao.platform.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

class SupabaseJwtAuthenticationConverterTests {

    private final SupabaseJwtAuthenticationConverter converter = new SupabaseJwtAuthenticationConverter();

    @Test
    void convertsOnlyTheSupabaseSubjectIntoTheExternalIdentity() {
        Jwt jwt = jwt(Map.of(
                "sub", "user-123",
                "tenant_id", "attacker-tenant",
                "user_metadata", Map.of("tenant_id", "another-tenant"),
                "role", "admin"));

        SupabaseAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication.getPrincipal())
                .isEqualTo(new ExternalSubject(ExternalSubject.SUPABASE_PROVIDER, "user-123"));
        assertThat(authentication.getAuthorities()).isEmpty();
        assertThat(authentication.getName()).isEqualTo("user-123");
    }

    @Test
    void rejectsMissingSubjectBeforeTheDomainIsCalled() {
        Jwt jwt = jwt(Map.of("aud", "authenticated"));

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("sub");
    }

    @Test
    void rejectsBlankSubjectBeforeTheDomainIsCalled() {
        Jwt jwt = jwt(Map.of("sub", "   "));

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("sub");
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "ES256")
                .claims(values -> values.putAll(claims))
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
