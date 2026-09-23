package br.com.taas.saas.gestaoproducao.ui.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;

import br.com.taas.saas.gestaoproducao.platform.access.application.model.AccessTokenContext;
import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseAuthenticationToken;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.AcceptInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAcceptanceException;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAcceptanceService;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

class InvitationAcceptanceViewTests {

    private static final String TOKEN = "opaque-invitation-token";
    private static final String SUBJECT = "invitee-subject";
    private static final String ACCESS_TOKEN = "validated-access-token";

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void openingInvitationOnlyShowsExplicitConfirmationAndDoesNotExposeToken() {
        InvitationAcceptanceService acceptanceService = mock(InvitationAcceptanceService.class);
        InvitationAcceptanceView view = new InvitationAcceptanceView(acceptanceService);
        view.setParameter(null, TOKEN);

        assertThat(textOf(view))
                .contains("Convite de acesso", "Confirme para ativar")
                .doesNotContain(TOKEN);
        assertThat(confirmButton(view).getText()).isEqualTo("Confirmar aceite do convite");
        assertThat(confirmButton(view).isEnabled()).isTrue();
        verifyNoInteractions(acceptanceService);
    }

    @Test
    void acceptsOnlyAfterExplicitConfirmationUsingValidatedSessionIdentity() {
        InvitationAcceptanceService acceptanceService = mock(InvitationAcceptanceService.class);
        InvitationAcceptanceView view = new InvitationAcceptanceView(acceptanceService);
        view.setParameter(null, TOKEN);
        SecurityContextHolder.getContext().setAuthentication(authentication());

        confirmButton(view).click();

        verify(acceptanceService).acceptInvitation(org.mockito.ArgumentMatchers.argThat(command ->
                command.token().equals(TOKEN)
                        && command.accessTokenContext().equals(new AccessTokenContext(
                                ExternalSubject.fromSupabase(SUBJECT), ACCESS_TOKEN))));
        assertThat(textOf(view)).contains("Convite aceito. Seu acesso está ativo.");
        assertThat(confirmButton(view).isEnabled()).isFalse();
    }

    @Test
    void unauthenticatedClickDoesNotCallAcceptanceService() {
        InvitationAcceptanceService acceptanceService = mock(InvitationAcceptanceService.class);
        InvitationAcceptanceView view = new InvitationAcceptanceView(acceptanceService);
        view.setParameter(null, TOKEN);

        confirmButton(view).click();

        verify(acceptanceService, never()).acceptInvitation(any(AcceptInvitationCommand.class));
        assertThat(textOf(view)).contains("Não foi possível aceitar este convite");
        assertThat(textOf(view)).doesNotContain(TOKEN, "invitee@example.com", "Tenant reservado");
    }

    @Test
    void domainRejectionIsShownAsGenericMessageWithoutSensitiveDetails() {
        InvitationAcceptanceService acceptanceService = mock(InvitationAcceptanceService.class);
        org.mockito.Mockito.doThrow(new InvitationAcceptanceException(
                new IllegalStateException(TOKEN + " invitee@example.com Tenant reservado")))
                .when(acceptanceService).acceptInvitation(any(AcceptInvitationCommand.class));
        InvitationAcceptanceView view = new InvitationAcceptanceView(acceptanceService);
        view.setParameter(null, TOKEN);
        SecurityContextHolder.getContext().setAuthentication(authentication());

        confirmButton(view).click();

        assertThat(textOf(view))
                .contains("Não foi possível aceitar este convite")
                .doesNotContain(TOKEN, "invitee@example.com", "Tenant reservado");
    }

    private static SupabaseAuthenticationToken authentication() {
        Jwt jwt = Jwt.withTokenValue(ACCESS_TOKEN)
                .header("alg", "ES256")
                .subject(SUBJECT)
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return new SupabaseAuthenticationToken(jwt, ExternalSubject.fromSupabase(SUBJECT));
    }

    private static Button confirmButton(InvitationAcceptanceView view) {
        return view.getChildren()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static String textOf(InvitationAcceptanceView view) {
        return view.getChildren()
                .map(Component::getElement)
                .map(element -> element.getText())
                .collect(Collectors.joining(" "));
    }
}
