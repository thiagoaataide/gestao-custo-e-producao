package br.com.taas.saas.gestaoproducao.platform.access.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

class SupabaseLocalLogoutHandlerTests {

    @Test
    void revokesOnlyTheCurrentSupabaseSession() {
        SupabaseAuthClient authClient = mock(SupabaseAuthClient.class);
        SupabaseLocalLogoutHandler handler = new SupabaseLocalLogoutHandler(authClient);
        SupabaseAuthenticationToken authentication = authentication();

        handler.logout(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication);

        verify(authClient).signOutLocal("access-token");
    }

    @Test
    void keepsLocalLogoutSuccessfulWhenSupabaseCannotBeReached() {
        SupabaseAuthClient authClient = mock(SupabaseAuthClient.class);
        doThrow(new SupabaseAuthClient.ProviderUnavailableException())
                .when(authClient).signOutLocal("access-token");
        SupabaseLocalLogoutHandler handler = new SupabaseLocalLogoutHandler(authClient);

        assertThatCode(() -> handler.logout(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                authentication()))
                .doesNotThrowAnyException();
    }

    private static SupabaseAuthenticationToken authentication() {
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "ES256")
                .subject("subject-123")
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return new SupabaseAuthenticationToken(
                jwt,
                ExternalSubject.fromSupabase("subject-123"),
                "refresh-token");
    }
}
