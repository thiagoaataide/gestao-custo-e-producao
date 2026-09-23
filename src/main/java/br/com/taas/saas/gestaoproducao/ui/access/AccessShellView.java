package br.com.taas.saas.gestaoproducao.ui.access;

import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;

import br.com.taas.saas.gestaoproducao.platform.administration.application.BootstrapOwnerService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.PlatformBootstrapDeniedException;
import br.com.taas.saas.gestaoproducao.platform.administration.config.PlatformBootstrapProperties;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

@Route("")
@PageTitle("Acesso | Gestão de Produção")
@AnonymousAllowed
public final class AccessShellView extends VerticalLayout {

    @Autowired
    public AccessShellView(
            AccessShellStateResolver stateResolver,
            AuthenticationContext authenticationContext,
            BootstrapOwnerService bootstrapOwnerService,
            PlatformBootstrapProperties bootstrapProperties) {
        this(stateResolver, currentAuthentication(), authenticationContext,
                bootstrapOwnerService, bootstrapProperties);
    }

    private AccessShellView(
            AccessShellStateResolver stateResolver,
            Authentication authentication,
            AuthenticationContext authenticationContext,
            BootstrapOwnerService bootstrapOwnerService,
            PlatformBootstrapProperties bootstrapProperties) {
        this(stateResolver.resolve(authentication), subjectFrom(authentication), authenticationContext,
                bootstrapOwnerService, bootstrapProperties);
    }

    AccessShellView(AccessShellState state) {
        this(state, null, null, null);
    }

    AccessShellView(
            AccessShellState state,
            ExternalSubject subject,
            BootstrapOwnerService bootstrapOwnerService,
            PlatformBootstrapProperties bootstrapProperties) {
        this(state, subject, null, bootstrapOwnerService, bootstrapProperties);
    }

    private AccessShellView(
            AccessShellState state,
            ExternalSubject subject,
            AuthenticationContext authenticationContext,
            BootstrapOwnerService bootstrapOwnerService,
            PlatformBootstrapProperties bootstrapProperties) {
        setWidthFull();
        setMaxWidth("42rem");
        setMargin(true);
        setSpacing(true);

        add(new H1("Gestão de Produção"));
        add(new H2(titleFor(state)));
        add(new Paragraph(messageFor(state)));
        add(actionFor(state, subject, bootstrapOwnerService, bootstrapProperties));
        if (state != AccessShellState.UNAUTHENTICATED && authenticationContext != null) {
            add(new Button("Sair", event -> authenticationContext.logout()));
        }
    }

    private static Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static ExternalSubject subjectFrom(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof ExternalSubject subject
                ? subject
                : null;
    }

    private static String titleFor(AccessShellState state) {
        return switch (state) {
            case UNAUTHENTICATED -> "Autenticação necessária";
            case PROVISIONED -> "Acesso provisionado";
            case NOT_PROVISIONED -> "Acesso não provisionado";
            case AMBIGUOUS_MEMBERSHIP -> "Acesso bloqueado";
            case PLATFORM_ACCESS -> "Acesso de plataforma";
        };
    }

    private static String messageFor(AccessShellState state) {
        return switch (state) {
            case UNAUTHENTICATED ->
                    "Inicie o fluxo de autenticação gerenciado pelo Supabase para continuar.";
            case PROVISIONED ->
                    "Seu acesso operacional está provisionado. A área operacional será disponibilizada pelas próximas features.";
            case NOT_PROVISIONED ->
                    "Seu usuário está autenticado, mas ainda não possui acesso operacional provisionado.";
            case AMBIGUOUS_MEMBERSHIP ->
                    "Não foi possível confirmar um único vínculo operacional. Solicite a correção do seu provisionamento.";
            case PLATFORM_ACCESS ->
                    "Seu perfil possui acesso de plataforma. Este shell não disponibiliza operações de tenant.";
        };
    }

    private static Component actionFor(
            AccessShellState state,
            ExternalSubject subject,
            BootstrapOwnerService bootstrapOwnerService,
            PlatformBootstrapProperties bootstrapProperties) {
        return switch (state) {
            case UNAUTHENTICATED -> new Anchor("login", "Iniciar autenticação");
            case PROVISIONED -> {
                Button entry = new Button("Entrar na aplicação");
                entry.setEnabled(false);
                entry.setTooltipText("A área operacional será disponibilizada pelas próximas features.");
                yield entry;
            }
            case NOT_PROVISIONED -> ownerBootstrapAction(
                    subject, bootstrapOwnerService, bootstrapProperties);
            case AMBIGUOUS_MEMBERSHIP -> new Paragraph("");
            case PLATFORM_ACCESS -> new Anchor("platform", "Abrir administração da plataforma");
        };
    }

    private static Component ownerBootstrapAction(
            ExternalSubject subject,
            BootstrapOwnerService bootstrapOwnerService,
            PlatformBootstrapProperties bootstrapProperties) {
        if (subject == null
                || bootstrapOwnerService == null
                || bootstrapProperties == null
                || !bootstrapProperties.authorizes(subject)) {
            return new Paragraph("");
        }

        Button bootstrap = new Button("Ativar administração da plataforma");
        bootstrap.addClickListener(event -> {
            try {
                bootstrapOwnerService.bootstrapOwner(subject, Instant.now());
                bootstrap.getUI().ifPresent(ui -> ui.navigate("platform"));
            } catch (PlatformBootstrapDeniedException exception) {
                Notification.show(
                        "Não foi possível ativar a administração. Verifique se já existe outro owner da plataforma.");
            }
        });
        return bootstrap;
    }
}
