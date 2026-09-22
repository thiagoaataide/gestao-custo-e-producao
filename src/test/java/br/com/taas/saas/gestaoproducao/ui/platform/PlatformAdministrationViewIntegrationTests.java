package br.com.taas.saas.gestaoproducao.ui.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;

import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.PlatformMembershipAdministrationView;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;

@SpringBootTest
@ActiveProfiles("test")
class PlatformAdministrationViewIntegrationTests {

    private static final UUID TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void ownerSeesPlatformAdministrationSectionsAndOwnerCommands() {
        PlatformAdministrationView view = new PlatformAdministrationView(
                new PlatformAdministrationViewState(
                        true,
                        true,
                        List.of(new Tenant(TENANT_ID, "Tenant sem usuário", TenantStatus.ACTIVE,
                                Instant.parse("2026-09-22T10:00:00Z"))),
                        List.of(),
                        new PlatformMembershipAdministrationView(List.of(), List.of())));

        String text = textOf(view);
        assertThat(text)
                .contains("Administração da plataforma")
                .contains("Tenants")
                .contains("Convites")
                .contains("Memberships")
                .contains("Papéis de plataforma")
                .contains("Tenants sem membership ativa: 1")
                .contains("Criar tenant")
                .contains("Conceder PLATFORM_ADMIN");
    }

    @Test
    void tenantUserDoesNotSeePlatformCommandsWhenRouteIsReachedDirectly() {
        PlatformAdministrationView view = new PlatformAdministrationView(
                PlatformAdministrationViewState.denied());

        assertThat(textOf(view))
                .contains("Acesso não autorizado")
                .doesNotContain("Criar tenant", "Criar convite", "Revogar", "Memberships");
        assertThat(buttons(view)).isEmpty();
    }

    @Test
    void commonPlatformAdminSeesPlatformReadOperationsWithoutOwnerControls() {
        PlatformAdministrationView view = new PlatformAdministrationView(
                new PlatformAdministrationViewState(
                        true,
                        false,
                        List.of(),
                        List.of(),
                        new PlatformMembershipAdministrationView(List.of(), List.of())));

        String text = textOf(view);
        assertThat(text)
                .contains("PLATFORM_ADMIN")
                .contains("Criar tenant")
                .contains("Criar convite")
                .doesNotContain("Conceder PLATFORM_ADMIN");
    }

    private static String textOf(PlatformAdministrationView view) {
        return textOfComponent(view);
    }

    private static List<Button> buttons(PlatformAdministrationView view) {
        return allComponents(view)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .toList();
    }

    private static String textOfComponent(Component component) {
        return component.getElement().getText() + " "
                + component.getChildren()
                        .map(PlatformAdministrationViewIntegrationTests::textOfComponent)
                        .collect(Collectors.joining(" "));
    }

    private static java.util.stream.Stream<Component> allComponents(Component component) {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(component),
                component.getChildren().flatMap(PlatformAdministrationViewIntegrationTests::allComponents));
    }
}
