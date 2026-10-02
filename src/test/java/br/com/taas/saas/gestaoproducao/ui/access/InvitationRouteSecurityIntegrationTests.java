package br.com.taas.saas.gestaoproducao.ui.access;

import static org.mockito.Mockito.verifyNoInteractions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseAuthClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InvitationRouteSecurityIntegrationTests {

    private static final String INVITATION_TOKEN = "opaque-invitation-token";

    @LocalServerPort
    private int port;

    @MockitoBean
    private SupabaseAuthClient supabaseAuthClient;

    @Test
    void invitationRouteIsPublicButOpeningItDoesNotAuthenticateOrAccept() throws Exception {
        var response = new RestTemplate().getForEntity(
                "http://localhost:" + port + "/invitations/" + INVITATION_TOKEN, String.class);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(200);

        verifyNoInteractions(supabaseAuthClient);
    }
}
