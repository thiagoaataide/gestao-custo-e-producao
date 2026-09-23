package br.com.taas.saas.gestaoproducao.ui.access;

import java.time.Instant;
import java.util.Objects;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

import br.com.taas.saas.gestaoproducao.platform.access.application.model.AccessTokenContext;
import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseAuthenticationToken;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.AcceptInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAcceptanceService;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

@Route("invitations")
@PageTitle("Aceitar convite | Gestão de Produção")
@PermitAll
public final class InvitationAcceptanceView extends VerticalLayout implements HasUrlParameter<String> {

    private static final String UNAVAILABLE_MESSAGE =
            "Não foi possível aceitar este convite. Confirme a conta autenticada ou solicite um novo link à administração.";

    private final InvitationAcceptanceService invitationAcceptanceService;
    private final Paragraph resultMessage = new Paragraph(
            "Confirme para ativar seu acesso ao espaço de produção associado ao convite.");
    private final Button confirmButton = new Button("Confirmar aceite do convite");

    private String token;

    public InvitationAcceptanceView(InvitationAcceptanceService invitationAcceptanceService) {
        this.invitationAcceptanceService = Objects.requireNonNull(
                invitationAcceptanceService,
                "invitationAcceptanceService must not be null");
        setWidthFull();
        setMaxWidth("42rem");
        setMargin(true);
        setSpacing(true);

        confirmButton.setEnabled(false);
        confirmButton.addClickListener(event -> acceptInvitation());
        add(new H1("Convite de acesso"), resultMessage, confirmButton);
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        token = parameter == null || parameter.isBlank() ? null : parameter;
        confirmButton.setEnabled(token != null);
        if (token == null) {
            resultMessage.setText(UNAVAILABLE_MESSAGE);
        }
    }

    private void acceptInvitation() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (token == null
                || !(authentication instanceof SupabaseAuthenticationToken supabaseAuthentication)
                || !supabaseAuthentication.isAuthenticated()
                || !(supabaseAuthentication.getPrincipal() instanceof ExternalSubject subject)) {
            showUnavailableResult();
            return;
        }

        confirmButton.setEnabled(false);
        try {
            invitationAcceptanceService.acceptInvitation(new AcceptInvitationCommand(
                    token,
                    new AccessTokenContext(subject, supabaseAuthentication.getJwt().getTokenValue()),
                    Instant.now()));
            resultMessage.setText("Convite aceito. Seu acesso está ativo.");
        } catch (RuntimeException exception) {
            showUnavailableResult();
        }
    }

    private void showUnavailableResult() {
        confirmButton.setEnabled(false);
        resultMessage.setText(UNAVAILABLE_MESSAGE);
    }
}
