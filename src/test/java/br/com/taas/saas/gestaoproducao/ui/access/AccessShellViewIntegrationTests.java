package br.com.taas.saas.gestaoproducao.ui.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;

import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecision;
import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseAuthenticationToken;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

@SpringBootTest
@ActiveProfiles("test")
class AccessShellViewIntegrationTests {

    private static final ExternalSubject PROVISIONED_SUBJECT =
            ExternalSubject.fromSupabase("test-subject-a");
    private static final ExternalSubject UNPROVISIONED_SUBJECT =
            ExternalSubject.fromSupabase("blocked-subject");

    @Autowired
    private AccessShellStateResolver stateResolver;

    @Test
    void presentsAuthenticationFlowWithoutProtectedContentWhenUnauthenticated() {
        AccessShellView view = new AccessShellView(AccessShellState.UNAUTHENTICATED);

        assertThat(textOf(view))
                .contains("Autenticação necessária")
                .contains("Inicie o fluxo de autenticação")
                .contains("Iniciar autenticação");
        assertThat(view.getChildren().filter(Anchor.class::isInstance)).hasSize(1);
        assertThat(buttons(view)).isEmpty();
    }

    @Test
    void presentsEntryStateForAProvisionedUser() {
        AccessShellState state = stateResolver.resolve(authenticationFor(PROVISIONED_SUBJECT));
        AccessShellView view = new AccessShellView(state);

        assertThat(state).isEqualTo(AccessShellState.PROVISIONED);
        assertThat(textOf(view))
                .contains("Acesso provisionado")
                .contains("Seu acesso operacional está provisionado");
        assertThat(buttons(view)).singleElement().satisfies(button -> {
            assertThat(button.getText()).isEqualTo("Entrar na aplicação");
            assertThat(button.isEnabled()).isFalse();
        });
    }

    @Test
    void blocksAUserWithoutProvisioningAndExposesNoOperationalCommand() {
        AccessShellState state = stateResolver.resolve(authenticationFor(UNPROVISIONED_SUBJECT));
        AccessShellView view = new AccessShellView(state);

        assertThat(state).isEqualTo(AccessShellState.NOT_PROVISIONED);
        assertThat(textOf(view))
                .contains("Acesso não provisionado")
                .contains("não possui acesso operacional provisionado");
        assertThat(buttons(view)).isEmpty();
        assertThat(textOf(view)).doesNotContain("TENANT_A", "tenant_id");
    }

    @Test
    void blocksAnAmbiguousMembershipWithoutSelectingATenant() {
        AccessShellState state = stateResolver.resolve(
                AccessDecision.ambiguousMembership(PROVISIONED_SUBJECT));
        AccessShellView view = new AccessShellView(state);

        assertThat(state).isEqualTo(AccessShellState.AMBIGUOUS_MEMBERSHIP);
        assertThat(textOf(view))
                .contains("Acesso bloqueado")
                .contains("único vínculo operacional");
        assertThat(buttons(view)).isEmpty();
        assertThat(textOf(view)).doesNotContain("TENANT_A", "tenant_id");
    }

    @Test
    void exposesPlatformAdministrationOnlyForPlatformAccess() {
        AccessShellView view = new AccessShellView(AccessShellState.PLATFORM_ACCESS);

        assertThat(view.getChildren().filter(Anchor.class::isInstance))
                .extracting(component -> ((Anchor) component).getHref())
                .containsExactly("platform");
        assertThat(textOf(view)).contains("Abrir administração da plataforma");
    }

    private static SupabaseAuthenticationToken authenticationFor(ExternalSubject subject) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "ES256")
                .subject(subject.value())
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        return new SupabaseAuthenticationToken(jwt, subject);
    }

    private static java.util.List<Button> buttons(AccessShellView view) {
        return view.getChildren()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .toList();
    }

    private static String textOf(AccessShellView view) {
        return view.getChildren()
                .map(Component::getElement)
                .map(element -> element.getText())
                .collect(Collectors.joining(" "));
    }
}
