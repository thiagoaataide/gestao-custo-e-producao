package br.com.taas.saas.gestaoproducao.ui.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.oauth2.jwt.Jwt;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;

import br.com.taas.saas.gestaoproducao.platform.access.application.model.AuthenticatedIdentityProfile;
import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseAuthClient;
import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseAuthenticationToken;
import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseInvitationAuthService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.AcceptInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAcceptanceException;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAcceptanceService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationOnboardingQueryService;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;

class InvitationAcceptanceViewTests {

    private static final String TOKEN = "opaque-invitation-token";
    private static final String INVITED_EMAIL = "invitee@outlook.com";
    private static final String SUBJECT = "invitee-subject";
    private static final String ACCESS_TOKEN = "validated-access-token";

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void signupIsInvitationScopedAndUsesInvitedEmail() {
        Fixture fixture = new Fixture();
        InvitationAcceptanceView view = fixture.view();
        view.setParameter(null, TOKEN);
        passwordField(view, "Crie uma senha").setValue("not-a-real-password");

        signupButton(view).click();

        verify(fixture.onboardingQuery).invitedEmail(org.mockito.ArgumentMatchers.eq(TOKEN), any());
        verify(fixture.invitationAuthService).signUp(INVITED_EMAIL, "not-a-real-password");
        assertThat(textOf(view)).contains(INVITED_EMAIL, "Enviamos um código");
        assertThat(field(view, "E-mail do convite").isReadOnly()).isTrue();
        assertThat(field(view, "E-mail do convite").getValue()).isEqualTo(INVITED_EMAIL);
        verifyNoInteractions(fixture.acceptanceService);
    }

    @Test
    void otpVerificationContinuesWithVerifiedInviteIdentity() {
        Fixture fixture = new Fixture();
        when(fixture.invitationAuthService.verifySignupOtp(INVITED_EMAIL, "123456"))
                .thenReturn(authentication(SUBJECT));
        when(fixture.invitationAuthService.verifiedProfile(any(SupabaseAuthenticationToken.class)))
                .thenReturn(Optional.of(profile(SUBJECT, INVITED_EMAIL)));
        InvitationAcceptanceView view = fixture.view();
        view.setParameter(null, TOKEN);
        field(view, "Código recebido por e-mail").setValue("123456");

        button(view, "Verificar e-mail").click();

        verify(fixture.invitationAuthService).verifySignupOtp(INVITED_EMAIL, "123456");
        assertThat(textOf(view)).contains("E-mail confirmado", INVITED_EMAIL);
        assertThat(button(view, "Confirmar aceite do convite").isEnabled()).isTrue();
        verifyNoInteractions(fixture.acceptanceService);
    }

    @Test
    void otherAuthenticatedAccountCanSwitchAndResumeSameInvitation() {
        Fixture fixture = new Fixture();
        SecurityContextHolder.getContext().setAuthentication(authentication("owner-gmail-subject"));
        when(fixture.invitationAuthService.verifiedProfile(any(SupabaseAuthenticationToken.class)))
                .thenReturn(Optional.of(profile("owner-gmail-subject", "owner@gmail.com")));
        InvitationAcceptanceView view = fixture.view();
        view.setParameter(null, TOKEN);

        assertThat(textOf(view)).contains("Troque para a conta convidada");
        assertThat(button(view, "Confirmar aceite do convite").isEnabled()).isFalse();
        button(view, "Trocar de conta e continuar convite").click();

        verify(fixture.invitationAuthService).signOut(ACCESS_TOKEN);
        verify(fixture.onboardingQuery).invitedEmail(org.mockito.ArgumentMatchers.eq(TOKEN), any());
        assertThat(button(view, "Criar conta e enviar código").isVisible()).isTrue();
        assertThat(field(view, "E-mail do convite").getValue()).isEqualTo(INVITED_EMAIL);
        verifyNoInteractions(fixture.acceptanceService);
    }

    @Test
    void unverifiedExistingAuthAccountMustCompleteOtpBeforeAcceptance() {
        Fixture fixture = new Fixture();
        SupabaseAuthenticationToken unverified = authentication("unverified-subject");
        SecurityContextHolder.getContext().setAuthentication(unverified);
        when(fixture.invitationAuthService.verifiedProfile(unverified)).thenReturn(Optional.empty());
        InvitationAcceptanceView view = fixture.view();
        view.setParameter(null, TOKEN);

        assertThat(textOf(view)).contains("Confirme o e-mail convidado");
        assertThat(field(view, "Código recebido por e-mail").isVisible()).isTrue();
        assertThat(button(view, "Confirmar aceite do convite").isEnabled()).isFalse();
        verify(fixture.acceptanceService, never()).acceptInvitation(any(AcceptInvitationCommand.class));
    }

    @Test
    void mismatchedVerifiedIdentityCannotAcceptInvitation() {
        Fixture fixture = new Fixture();
        SecurityContextHolder.getContext().setAuthentication(authentication("owner-gmail-subject"));
        when(fixture.invitationAuthService.verifiedProfile(any(SupabaseAuthenticationToken.class)))
                .thenReturn(Optional.of(profile("owner-gmail-subject", "owner@gmail.com")));
        InvitationAcceptanceView view = fixture.view();
        view.setParameter(null, TOKEN);

        view.acceptInvitation();

        assertThat(button(view, "Confirmar aceite do convite").isEnabled()).isFalse();
        verify(fixture.acceptanceService, never()).acceptInvitation(any(AcceptInvitationCommand.class));
    }

    @Test
    void openingInvitationOnlyShowsExplicitConfirmationAndDoesNotExposeToken() {
        Fixture fixture = new Fixture();
        InvitationAcceptanceView view = fixture.view();
        view.setParameter(null, TOKEN);

        assertThat(textOf(view)).contains("Convite de acesso", INVITED_EMAIL)
                .doesNotContain(TOKEN);
        assertThat(button(view, "Confirmar aceite do convite").isVisible()).isFalse();
        verifyNoInteractions(fixture.acceptanceService);
    }

    @Test
    void acceptsOnlyAfterExplicitConfirmationUsingVerifiedMatchingSessionIdentity() {
        Fixture fixture = new Fixture();
        SupabaseAuthenticationToken authentication = authentication(SUBJECT);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(fixture.invitationAuthService.verifiedProfile(authentication))
                .thenReturn(Optional.of(profile(SUBJECT, INVITED_EMAIL)));
        InvitationAcceptanceView view = fixture.view();
        view.setParameter(null, TOKEN);

        verifyNoInteractions(fixture.acceptanceService);
        button(view, "Confirmar aceite do convite").click();

        verify(fixture.acceptanceService).acceptInvitation(org.mockito.ArgumentMatchers.argThat(command ->
                command.token().equals(TOKEN)
                        && command.accessTokenContext().equals(new br.com.taas.saas.gestaoproducao.platform.access.application.model.AccessTokenContext(
                                ExternalSubject.fromSupabase(SUBJECT), ACCESS_TOKEN))));
        assertThat(textOf(view)).contains("Convite aceito. Seu acesso está ativo.");
        assertThat(button(view, "Confirmar aceite do convite").isEnabled()).isFalse();
    }

    @Test
    void invalidOtpDoesNotAcceptInviteOrShowProviderDetails() {
        Fixture fixture = new Fixture();
        org.mockito.Mockito.doThrow(new SupabaseAuthClient.OtpRejectedException())
                .when(fixture.invitationAuthService).verifySignupOtp(INVITED_EMAIL, "000000");
        InvitationAcceptanceView view = fixture.view();
        view.setParameter(null, TOKEN);
        field(view, "Código recebido por e-mail").setValue("000000");

        button(view, "Verificar e-mail").click();

        assertThat(textOf(view)).contains("Não foi possível verificar o código")
                .doesNotContain(TOKEN, "Senha", "subject");
        verifyNoInteractions(fixture.acceptanceService);
    }

    private static SupabaseAuthenticationToken authentication(String subject) {
        Jwt jwt = Jwt.withTokenValue(ACCESS_TOKEN)
                .header("alg", "ES256")
                .subject(subject)
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return new SupabaseAuthenticationToken(jwt, ExternalSubject.fromSupabase(subject));
    }

    private static AuthenticatedIdentityProfile profile(String subject, String email) {
        return new AuthenticatedIdentityProfile(
                ExternalSubject.fromSupabase(subject), NormalizedEmail.from(email), true);
    }

    private static Button button(InvitationAcceptanceView view, String label) {
        return view.getChildren().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> label.equals(candidate.getText())).findFirst().orElseThrow();
    }

    private static Button signupButton(InvitationAcceptanceView view) {
        return button(view, "Criar conta e enviar código");
    }

    private static TextField field(InvitationAcceptanceView view, String label) {
        return view.getChildren().filter(TextField.class::isInstance).map(TextField.class::cast)
                .filter(candidate -> label.equals(candidate.getLabel())).findFirst().orElseThrow();
    }

    private static PasswordField passwordField(InvitationAcceptanceView view, String label) {
        return view.getChildren().filter(PasswordField.class::isInstance).map(PasswordField.class::cast)
                .filter(candidate -> label.equals(candidate.getLabel())).findFirst().orElseThrow();
    }

    private static String textOf(InvitationAcceptanceView view) {
        return view.getChildren().map(Component::getElement).map(element -> element.getText())
                .collect(Collectors.joining(" "));
    }

    private static final class Fixture {
        final InvitationAcceptanceService acceptanceService = mock(InvitationAcceptanceService.class);
        final InvitationOnboardingQueryService onboardingQuery = mock(InvitationOnboardingQueryService.class);
        final SupabaseInvitationAuthService invitationAuthService = mock(SupabaseInvitationAuthService.class);
        final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        final SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);

        Fixture() {
            when(onboardingQuery.invitedEmail(org.mockito.ArgumentMatchers.eq(TOKEN), any()))
                    .thenReturn(Optional.of(INVITED_EMAIL));
        }

        InvitationAcceptanceView view() {
            return new InvitationAcceptanceView(
                    acceptanceService, onboardingQuery, invitationAuthService,
                    authenticationManager, securityContextRepository);
        }
    }
}
