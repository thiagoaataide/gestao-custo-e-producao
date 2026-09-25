package br.com.taas.saas.gestaoproducao.ui.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.oauth2.jwt.Jwt;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;

import br.com.taas.saas.gestaoproducao.platform.access.application.model.AuthenticatedIdentityProfile;
import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseAuthClient;
import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseAuthenticationToken;
import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseInvitationAuthService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAcceptanceService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationOnboardingQueryService;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;

class InvitationOnboardingViewTests {

    private static final String TOKEN = "opaque-invitation-token";
    private static final String EMAIL = "invitee@outlook.com";
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
        field(view, "Crie uma senha", PasswordField.class).setValue("not-a-real-password");

        button(view, "Criar conta e enviar código").click();

        verify(fixture.onboardingQuery).invitedEmail(org.mockito.ArgumentMatchers.eq(TOKEN), any());
        verify(fixture.invitationAuthService).signUp(EMAIL, "not-a-real-password");
        assertThat(field(view, "E-mail do convite", TextField.class).isReadOnly()).isTrue();
        assertThat(field(view, "E-mail do convite", TextField.class).getValue()).isEqualTo(EMAIL);
        assertThat(field(view, "Código recebido por e-mail", TextField.class).isVisible()).isTrue();
        assertThat(button(view, "Entrar com conta existente").isVisible()).isTrue();
        verifyNoInteractions(fixture.acceptanceService);
    }

    @Test
    void signupRejectionKeepsExistingAccountLoginAvailableForTheInvitedEmail() {
        Fixture fixture = new Fixture();
        SupabaseAuthenticationToken existing = authentication("invitee-subject");
        org.mockito.Mockito.doThrow(new SupabaseAuthClient.SignupRejectedException())
                .when(fixture.invitationAuthService).signUp(EMAIL, "existing-account-password");
        when(fixture.authenticationManager.authenticate(any()))
                .thenReturn(existing);
        when(fixture.invitationAuthService.verifiedProfile(existing))
                .thenReturn(Optional.of(profile("invitee-subject", EMAIL)));
        InvitationAcceptanceView view = fixture.view();
        view.setParameter(null, TOKEN);
        field(view, "Crie uma senha", PasswordField.class).setValue("existing-account-password");
        button(view, "Criar conta e enviar código").click();

        assertThat(button(view, "Entrar com conta existente").isVisible()).isTrue();
        assertThat(field(view, "Senha da conta existente", PasswordField.class).isVisible()).isTrue();
        assertThat(field(view, "E-mail do convite", TextField.class).getValue()).isEqualTo(EMAIL);
        field(view, "Senha da conta existente", PasswordField.class).setValue("existing-account-password");
        button(view, "Entrar com conta existente").click();

        verify(fixture.authenticationManager).authenticate(org.mockito.ArgumentMatchers.argThat(authentication ->
                authentication instanceof UsernamePasswordAuthenticationToken
                        && authentication.getName().equals(EMAIL)
                        && authentication.getCredentials().equals("existing-account-password")));
        assertThat(button(view, "Confirmar aceite do convite").isEnabled()).isTrue();
    }

    @Test
    void resendWithoutConfirmedDeliveryKeepsExistingAccountLoginAvailable() {
        Fixture fixture = new Fixture();
        org.mockito.Mockito.doThrow(new SupabaseAuthClient.SignupRejectedException())
                .when(fixture.invitationAuthService).resendSignupOtp(EMAIL);
        InvitationAcceptanceView view = fixture.view();
        view.setParameter(null, TOKEN);

        button(view, "Enviar ou reenviar código").click();

        assertThat(button(view, "Entrar com conta existente").isVisible()).isTrue();
        assertThat(field(view, "Senha da conta existente", PasswordField.class).isVisible()).isTrue();
        assertThat(field(view, "E-mail do convite", TextField.class).getValue()).isEqualTo(EMAIL);
        assertThat(field(view, "Código recebido por e-mail", TextField.class).isVisible()).isTrue();
    }

    @Test
    void otpVerificationContinuesWithVerifiedInviteIdentity() {
        Fixture fixture = new Fixture();
        SupabaseAuthenticationToken verified = authentication("invitee-subject");
        when(fixture.invitationAuthService.verifySignupOtp(EMAIL, "123456")).thenReturn(verified);
        when(fixture.invitationAuthService.verifiedProfile(verified))
                .thenReturn(Optional.of(profile("invitee-subject", EMAIL)));
        InvitationAcceptanceView view = fixture.view();
        view.setParameter(null, TOKEN);
        field(view, "Código recebido por e-mail", TextField.class).setValue("123456");

        button(view, "Verificar e-mail").click();

        verify(fixture.invitationAuthService).verifySignupOtp(EMAIL, "123456");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(verified);
        assertThat(button(view, "Confirmar aceite do convite").isEnabled()).isTrue();
        verifyNoInteractions(fixture.acceptanceService);
    }

    @Test
    void otherAuthenticatedAccountCanSwitchAndResumeSameInvitation() {
        Fixture fixture = new Fixture();
        SupabaseAuthenticationToken owner = authentication("owner-gmail-subject");
        SecurityContextHolder.getContext().setAuthentication(owner);
        when(fixture.invitationAuthService.verifiedProfile(owner))
                .thenReturn(Optional.of(profile("owner-gmail-subject", "owner@gmail.com")));
        InvitationAcceptanceView view = fixture.view();
        view.setParameter(null, TOKEN);

        assertThat(button(view, "Trocar de conta e continuar convite").isVisible()).isTrue();
        assertThat(button(view, "Confirmar aceite do convite").isEnabled()).isFalse();
        button(view, "Trocar de conta e continuar convite").click();

        verify(fixture.invitationAuthService).signOut(ACCESS_TOKEN);
        verify(fixture.onboardingQuery).invitedEmail(org.mockito.ArgumentMatchers.eq(TOKEN), any());
        assertThat(button(view, "Criar conta e enviar código").isVisible()).isTrue();
        assertThat(field(view, "E-mail do convite", TextField.class).getValue()).isEqualTo(EMAIL);
        verifyNoInteractions(fixture.acceptanceService);
    }

    private static <T extends com.vaadin.flow.component.Component> T field(
            InvitationAcceptanceView view, String label, Class<T> type) {
        return view.getChildren().filter(type::isInstance).map(type::cast)
                .filter(component -> component instanceof TextField textField
                        && label.equals(textField.getLabel())
                        || component instanceof PasswordField passwordField
                        && label.equals(passwordField.getLabel()))
                .findFirst().orElseThrow();
    }

    private static Button button(InvitationAcceptanceView view, String label) {
        return view.getChildren().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> label.equals(candidate.getText())).findFirst().orElseThrow();
    }

    private static SupabaseAuthenticationToken authentication(String subject) {
        Jwt jwt = Jwt.withTokenValue(ACCESS_TOKEN).header("alg", "ES256").subject(subject)
                .issuedAt(Instant.now().minusSeconds(30)).expiresAt(Instant.now().plusSeconds(300)).build();
        return new SupabaseAuthenticationToken(jwt, ExternalSubject.fromSupabase(subject));
    }

    private static AuthenticatedIdentityProfile profile(String subject, String email) {
        return new AuthenticatedIdentityProfile(
                ExternalSubject.fromSupabase(subject), NormalizedEmail.from(email), true);
    }

    private static final class Fixture {
        final InvitationAcceptanceService acceptanceService = mock(InvitationAcceptanceService.class);
        final InvitationOnboardingQueryService onboardingQuery = mock(InvitationOnboardingQueryService.class);
        final SupabaseInvitationAuthService invitationAuthService = mock(SupabaseInvitationAuthService.class);
        final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        final SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);

        Fixture() {
            when(onboardingQuery.invitedEmail(org.mockito.ArgumentMatchers.eq(TOKEN), any()))
                    .thenReturn(Optional.of(EMAIL));
        }

        InvitationAcceptanceView view() {
            return new InvitationAcceptanceView(acceptanceService, onboardingQuery,
                    invitationAuthService, authenticationManager, securityContextRepository);
        }
    }
}
