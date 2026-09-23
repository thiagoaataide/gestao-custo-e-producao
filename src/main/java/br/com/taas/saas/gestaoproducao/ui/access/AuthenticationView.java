package br.com.taas.saas.gestaoproducao.ui.access;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Autenticação | Gestão de Produção")
@AnonymousAllowed
public final class AuthenticationView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm;

    public AuthenticationView() {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        VerticalLayout content = new VerticalLayout();
        content.setWidthFull();
        content.setMaxWidth("30rem");
        content.setPadding(true);
        content.setSpacing(true);

        loginForm = new LoginForm();
        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(false);
        loginForm.setI18n(portugueseLoginI18n());

        content.add(new H1("Gestão de Produção"));
        content.add(new Paragraph("Entre com seu e-mail e senha para acessar a plataforma."));
        content.add(loginForm);
        content.add(new Anchor("/", "Voltar"));
        add(content);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")
                || event.getLocation().getQueryParameters().getParameters().containsKey("expired")) {
            loginForm.setError(true);
        }
    }

    private static LoginI18n portugueseLoginI18n() {
        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("Acessar conta");
        i18n.getForm().setUsername("E-mail");
        i18n.getForm().setPassword("Senha");
        i18n.getForm().setSubmit("Entrar");
        i18n.getForm().setForgotPassword("");
        i18n.getErrorMessage().setTitle("Não foi possível entrar");
        i18n.getErrorMessage().setMessage(
                "Confira seus dados e tente novamente. Se o problema persistir, tente mais tarde.");
        return i18n;
    }
}
