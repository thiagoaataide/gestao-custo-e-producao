package br.com.taas.saas.gestaoproducao.ui.access;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseAuthClient;

@SpringBootTest
@ActiveProfiles("test")
class InvitationRouteSecurityIntegrationTests {

    private static final String INVITATION_TOKEN = "opaque-invitation-token";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @MockitoBean
    private SupabaseAuthClient supabaseAuthClient;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void invitationRouteIsPublicButOpeningItDoesNotAuthenticateOrAccept() throws Exception {
        mockMvc.perform(get("/invitations/{token}", INVITATION_TOKEN))
                .andExpect(status().isOk());

        verifyNoInteractions(supabaseAuthClient);
    }
}
