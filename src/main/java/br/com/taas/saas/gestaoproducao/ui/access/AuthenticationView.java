package br.com.taas.saas.gestaoproducao.ui.access;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Autenticação | Gestão de Produção")
@AnonymousAllowed
public final class AuthenticationView extends VerticalLayout {

    public AuthenticationView() {
        setWidthFull();
        setMaxWidth("42rem");
        setMargin(true);
        setSpacing(true);

        add(new H1("Autenticação"));
        add(new Paragraph(
                "A autenticação desta aplicação é gerenciada pelo Supabase. "
                        + "Conclua o fluxo de login configurado para retornar ao shell de acesso."));
        add(new Anchor("", "Voltar ao shell de acesso"));
    }
}
