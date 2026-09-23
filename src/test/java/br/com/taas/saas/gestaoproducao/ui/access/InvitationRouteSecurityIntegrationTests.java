package br.com.taas.saas.gestaoproducao.ui.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseAuthClient;

@SpringBootTest
@ActiveProfiles("test")
class InvitationRouteSecurityIntegrationTests {

    private static final String INVITATION_TOKEN = "opaque-invitation-token";
    private static final String EMAIL = "invitee@example.com";
    private static final String PASSWORD = "not-a-real-password";
    private static final String ACCESS_TOKEN = "validated-test-access-token";
    private static final String REFRESH_TOKEN = "validated-test-refresh-token";
    private static final String SUBJECT = "invitation-route-subject";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @MockitoBean
    private SupabaseAuthClient supabaseAuthClient;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void unauthenticatedInviteRequestIsSavedAndSuccessfulLoginReturnsToTheSamePath() throws Exception {
        MvcResult inviteResponse = mockMvc.perform(get("/invitations/{token}", INVITATION_TOKEN))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) inviteResponse.getRequest().getSession(false);
        assertThat(session).isNotNull();
        SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(
                inviteResponse.getRequest(), inviteResponse.getResponse());
        assertThat(savedRequest).isNotNull();
        assertThat(savedRequest.getRedirectUrl())
                .contains("/invitations/" + INVITATION_TOKEN)
                .endsWith("?continue");
        verifyNoInteractions(supabaseAuthClient);

        HttpSessionCsrfTokenRepository csrfRepository = new HttpSessionCsrfTokenRepository();
        MockHttpServletRequest csrfRequest = new MockHttpServletRequest();
        csrfRequest.setSession(session);
        CsrfToken csrfToken = csrfRepository.generateToken(csrfRequest);
        csrfRepository.saveToken(csrfToken, csrfRequest, new MockHttpServletResponse());

        when(supabaseAuthClient.signInWithPassword(EMAIL, PASSWORD)).thenReturn(
                new SupabaseAuthClient.SupabaseAuthSession(ACCESS_TOKEN, REFRESH_TOKEN, SUBJECT));
        when(jwtDecoder.decode(ACCESS_TOKEN)).thenReturn(jwt());

        MvcResult loginResponse = mockMvc.perform(post("/login")
                        .session(session)
                        .param("username", EMAIL)
                        .param("password", PASSWORD)
                        .param(csrfToken.getParameterName(), csrfToken.getToken()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        verify(supabaseAuthClient).signInWithPassword(EMAIL, PASSWORD);
        assertThat(loginResponse.getResponse().getRedirectedUrl())
                .contains("/invitations/" + INVITATION_TOKEN)
                .endsWith("?continue");
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue(ACCESS_TOKEN)
                .header("alg", "ES256")
                .subject(SUBJECT)
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
