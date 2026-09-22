package br.com.taas.saas.gestaoproducao.ui.access;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("")
@PageTitle("Acesso | Gestão de Produção")
@AnonymousAllowed
public final class AccessShellView extends VerticalLayout {

    public AccessShellView(AccessShellStateResolver stateResolver) {
        this(stateResolver.resolve(currentAuthentication()));
    }

    AccessShellView(AccessShellState state) {
        setWidthFull();
        setMaxWidth("42rem");
        setMargin(true);
        setSpacing(true);

        add(new H1("Gestão de Produção"));
        add(new H2(titleFor(state)));
        add(new Paragraph(messageFor(state)));
        add(actionFor(state));
    }

    private static Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
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

    private static Component actionFor(AccessShellState state) {
        return switch (state) {
            case UNAUTHENTICATED -> new Anchor("login", "Iniciar autenticação");
            case PROVISIONED -> {
                Button entry = new Button("Entrar na aplicação");
                entry.setEnabled(false);
                entry.setTooltipText("A área operacional será disponibilizada pelas próximas features.");
                yield entry;
            }
            case NOT_PROVISIONED, AMBIGUOUS_MEMBERSHIP -> new Paragraph("");
            case PLATFORM_ACCESS -> new Anchor("platform", "Abrir administração da plataforma");
        };
    }
}
