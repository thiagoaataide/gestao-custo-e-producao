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
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.tabs.TabSheet;

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
                .contains("Membros")
                .contains("Papéis da plataforma")
                .contains("Auditoria")
                .contains("Tenants sem membro ativo: 1")
                .contains("Criar tenant")
                .contains("Conceder PLATFORM_ADMIN");

        TabSheet tabs = component(view, TabSheet.class);
        assertThat(tabs.getTabCount()).isEqualTo(4);
        assertThat(tabs.getSelectedIndex()).isZero();
    }

    @Test
    void memberInviteActionOpensInvitationsTab() {
        PlatformAdministrationView view = new PlatformAdministrationView(
                new PlatformAdministrationViewState(
                        true,
                        true,
                        List.of(new Tenant(TENANT_ID, "Tenant", TenantStatus.ACTIVE,
                                Instant.parse("2026-09-22T10:00:00Z"))),
                        List.of(),
                        new PlatformMembershipAdministrationView(List.of(), List.of())));
        TabSheet tabs = component(view, TabSheet.class);
        tabs.setSelectedIndex(2);

        button(view, "Convidar membro").click();

        assertThat(tabs.getSelectedIndex()).isEqualTo(1);
    }

    @Test
    void formsAndGridsUseResponsiveAndContentSizedLayouts() {
        PlatformAdministrationView view = new PlatformAdministrationView(
                new PlatformAdministrationViewState(
                        true,
                        true,
                        List.of(),
                        List.of(),
                        new PlatformMembershipAdministrationView(List.of(), List.of())));

        FormLayout form = component(view, FormLayout.class);
        assertThat(form.getResponsiveSteps()).hasSize(3);
        assertThat(allComponents(view)
                .filter(Grid.class::isInstance)
                .map(Grid.class::cast)
                .allMatch(Grid::isAllRowsVisible)).isTrue();
    }

    @Test
    void tenantUserDoesNotSeePlatformCommandsWhenRouteIsReachedDirectly() {
        PlatformAdministrationView view = new PlatformAdministrationView(
                PlatformAdministrationViewState.denied());

        assertThat(textOf(view))
                .contains("Acesso não autorizado")
                .doesNotContain("Criar tenant", "Criar convite", "Convidar membro", "Revogar", "Membros");
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
                .contains("Membros")
                .doesNotContain("Conceder PLATFORM_ADMIN");
    }

    private static Button button(PlatformAdministrationView view, String text) {
        return allComponents(view)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static <T extends Component> T component(PlatformAdministrationView view, Class<T> type) {
        return allComponents(view)
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
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
                + childComponents(component)
                        .map(PlatformAdministrationViewIntegrationTests::textOfComponent)
                        .collect(Collectors.joining(" "));
    }

    private static java.util.stream.Stream<Component> allComponents(Component component) {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(component),
                childComponents(component).flatMap(PlatformAdministrationViewIntegrationTests::allComponents));
    }

    private static java.util.stream.Stream<Component> childComponents(Component component) {
        java.util.stream.Stream<Component> children = component.getChildren();
        if (component instanceof TabSheet tabs) {
            java.util.stream.Stream<Component> tabContents = java.util.stream.IntStream
                    .range(0, tabs.getTabCount())
                    .mapToObj(index -> tabs.getComponent(tabs.getTabAt(index)));
            return java.util.stream.Stream.concat(children, tabContents).distinct();
        }
        return children;
    }
}
