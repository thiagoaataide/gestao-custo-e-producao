package br.com.taas.saas.gestaoproducao.ui.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.login.LoginForm;

class AuthenticationViewTests {

    @Test
    void providesTheVaadinLoginFormPostingToSpringSecurity() {
        AuthenticationView view = new AuthenticationView();

        LoginForm loginForm = view.getChildren()
                .flatMap(component -> component.getChildren())
                .filter(LoginForm.class::isInstance)
                .map(LoginForm.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(loginForm.getElement().getProperty("action")).isEqualTo("login");
        assertThat(loginForm.isForgotPasswordButtonVisible()).isFalse();
    }
}
