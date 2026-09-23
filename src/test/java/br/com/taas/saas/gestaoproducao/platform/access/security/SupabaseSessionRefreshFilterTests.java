package br.com.taas.saas.gestaoproducao.platform.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

class SupabaseSessionRefreshFilterTests {

    private static final String SUBJECT = "subject-123";
    private static final String ACCESS_TOKEN = "old-access-token";
    private static final String REFRESH_TOKEN = "old-refresh-token";

    private SupabaseAuthClient authClient;
    private JwtDecoder jwtDecoder;
    private HttpSessionSecurityContextRepository contextRepository;
    private SupabaseSessionRefreshFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        authClient = mock(SupabaseAuthClient.class);
        jwtDecoder = mock(JwtDecoder.class);
        contextRepository = new HttpSessionSecurityContextRepository();
        filter = new SupabaseSessionRefreshFilter(
                authClient,
                jwtDecoder,
                new SupabaseJwtAuthenticationConverter(),
                contextRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savesTheRotatedTokenPairBackIntoTheExistingServerSession() throws Exception {
        MockHttpServletRequest request = requestWithAuthentication(jwt(ACCESS_TOKEN, SUBJECT, 30));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authClient.refresh(REFRESH_TOKEN)).thenReturn(new SupabaseAuthClient.SupabaseAuthSession(
                "new-access-token",
                "new-refresh-token",
                SUBJECT));
        when(jwtDecoder.decode("new-access-token")).thenReturn(jwt("new-access-token", SUBJECT, 300));
        AtomicBoolean chainInvoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
        SecurityContext saved = (SecurityContext) request.getSession(false).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        SupabaseAuthenticationToken authentication = (SupabaseAuthenticationToken) saved.getAuthentication();
        assertThat(authentication.getJwt().getTokenValue()).isEqualTo("new-access-token");
        assertThat(authentication.refreshToken()).isEqualTo("new-refresh-token");
        verify(authClient).refresh(REFRESH_TOKEN);
    }

    @Test
    void keepsAnUnexpiredSessionWhenSupabaseIsTemporarilyUnavailable() throws Exception {
        MockHttpServletRequest request = requestWithAuthentication(jwt(ACCESS_TOKEN, SUBJECT, 30));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authClient.refresh(REFRESH_TOKEN))
                .thenThrow(new SupabaseAuthClient.ProviderUnavailableException());
        AtomicBoolean chainInvoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
        SecurityContext saved = (SecurityContext) request.getSession(false).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(((SupabaseAuthenticationToken) saved.getAuthentication()).getJwt().getTokenValue())
                .isEqualTo(ACCESS_TOKEN);
    }

    @Test
    void invalidatesAnExpiredSessionWhenRefreshCannotBeCompleted() throws Exception {
        MockHttpServletRequest request = requestWithAuthentication(jwt(ACCESS_TOKEN, SUBJECT, -1));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authClient.refresh(REFRESH_TOKEN))
                .thenThrow(new SupabaseAuthClient.ProviderUnavailableException());
        AtomicBoolean chainInvoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        });

        assertThat(chainInvoked).isTrue();
        verify(authClient).refresh(REFRESH_TOKEN);
    }

    @Test
    void doesNotRefreshAStillFreshAccessToken() throws Exception {
        MockHttpServletRequest request = requestWithAuthentication(jwt(ACCESS_TOKEN, SUBJECT, 300));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
        verifyNoInteractions(authClient);
    }

    @Test
    void failsClosedWhenTheRotatedJwtBelongsToAnotherSubject() throws Exception {
        MockHttpServletRequest request = requestWithAuthentication(jwt(ACCESS_TOKEN, SUBJECT, 30));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authClient.refresh(REFRESH_TOKEN)).thenReturn(new SupabaseAuthClient.SupabaseAuthSession(
                "other-access-token",
                "other-refresh-token",
                "other-subject"));
        when(jwtDecoder.decode("other-access-token"))
                .thenReturn(jwt("other-access-token", "other-subject", 300));
        AtomicBoolean chainInvoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            chainInvoked.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        });

        assertThat(chainInvoked).isTrue();
    }

    @Test
    void serializesConcurrentRefreshesForTheSameHttpSession() throws Exception {
        MockHttpServletRequest firstRequest = requestWithAuthentication(jwt(ACCESS_TOKEN, SUBJECT, 30));
        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        secondRequest.setSession(firstRequest.getSession(false));
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch permitRefresh = new CountDownLatch(1);
        CountDownLatch secondRequestStarted = new CountDownLatch(1);
        AtomicInteger refreshCalls = new AtomicInteger();
        when(authClient.refresh(REFRESH_TOKEN)).thenAnswer(invocation -> {
            refreshCalls.incrementAndGet();
            refreshStarted.countDown();
            if (!permitRefresh.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to complete the refresh");
            }
            return new SupabaseAuthClient.SupabaseAuthSession(
                    "rotated-access-token",
                    "rotated-refresh-token",
                    SUBJECT);
        });
        when(jwtDecoder.decode("rotated-access-token"))
                .thenReturn(jwt("rotated-access-token", SUBJECT, 300));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> processRequest(firstRequest, firstResponse));
            assertThat(refreshStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> {
                secondRequestStarted.countDown();
                processRequest(secondRequest, secondResponse);
            });
            assertThat(secondRequestStarted.await(5, TimeUnit.SECONDS)).isTrue();
            permitRefresh.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertThat(refreshCalls).hasValue(1);
        SecurityContext saved = (SecurityContext) firstRequest.getSession(false).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        SupabaseAuthenticationToken authentication = (SupabaseAuthenticationToken) saved.getAuthentication();
        assertThat(authentication.refreshToken()).isEqualTo("rotated-refresh-token");
    }

    private void processRequest(
            MockHttpServletRequest request,
            MockHttpServletResponse response) {
        SecurityContextHolder.setContext(contextRepository.loadDeferredContext(request).get());
        try {
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private MockHttpServletRequest requestWithAuthentication(Jwt jwt) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        SupabaseAuthenticationToken authentication = new SupabaseAuthenticationToken(
                jwt,
                ExternalSubject.fromSupabase(SUBJECT),
                REFRESH_TOKEN);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, new MockHttpServletResponse());
        return request;
    }

    private static Jwt jwt(String token, String subject, long expiresInSeconds) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
                .header("alg", "ES256")
                .subject(subject)
                .issuedAt(now.minusSeconds(5))
                .expiresAt(now.plusSeconds(expiresInSeconds))
                .build();
    }
}
