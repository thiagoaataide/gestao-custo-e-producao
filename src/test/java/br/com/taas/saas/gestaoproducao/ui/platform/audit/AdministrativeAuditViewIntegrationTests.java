package br.com.taas.saas.gestaoproducao.ui.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextField;

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationDeniedException;
import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationService;
import br.com.taas.saas.gestaoproducao.platform.access.security.SupabaseAuthenticationToken;
import br.com.taas.saas.gestaoproducao.platform.administration.application.PlatformActorIdentityResolver;
import br.com.taas.saas.gestaoproducao.platform.administration.application.audit.AdministrativeAuditQueryService;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditMetadata;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditTargetType;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

@SpringBootTest
@ActiveProfiles("test")
class AdministrativeAuditViewIntegrationTests {

    private static final UUID ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID TARGET_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000201");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerAndPlatformAdminSeeOnlyAdministrativeAuditMetadata() {
        AdministrativeAuditView view = new AdministrativeAuditView(
                new AdministrativeAuditViewState(
                        true,
                        new AuditEventPage(
                                List.of(event(AuditAction.TENANT_CREATED, AuditResult.SUCCESS)),
                                0,
                                20,
                                1),
                        ""));

        assertThat(textOf(view))
                .contains("Auditoria administrativa")
                .doesNotContain("token", "segredo", "operations", "produção", "estoque");
        Grid<AuditEvent> grid = component(view, Grid.class);
        assertThat(grid.getListDataView().getItems())
                .extracting(AuditEvent::action)
                .containsExactly(AuditAction.TENANT_CREATED);
        assertThat(grid.getListDataView().getItems())
                .extracting(AuditEvent::targetType)
                .containsExactly(AuditTargetType.TENANT);
        assertThat(grid.getListDataView().getItems())
                .extracting(AuditEvent::result)
                .containsExactly(AuditResult.SUCCESS);
    }

    @Test
    void tenantUserAndUnprovisionedUserAreDeniedBeforeAuditQuery() {
        AdministrativeAuditQueryService queryService = mock(AdministrativeAuditQueryService.class);
        PlatformActorIdentityResolver actorResolver = mock(PlatformActorIdentityResolver.class);
        PlatformAuthorizationService authorization = mock(PlatformAuthorizationService.class);
        when(actorResolver.requireIdentityId(any())).thenReturn(ACTOR_ID);
        doThrow(new PlatformAuthorizationDeniedException())
                .when(authorization).requirePlatformAccess(ACTOR_ID);

        AdministrativeAuditView view = withAuthentication(() -> new AdministrativeAuditView(
                queryService,
                actorResolver,
                authorization));

        assertThat(textOf(view))
                .contains("Acesso não autorizado à auditoria administrativa.")
                .doesNotContain("Filtros", "TENANT_CREATED");
        assertThat(buttons(view)).isEmpty();
        verifyNoInteractions(queryService);
    }

    @Test
    void emptyAuditResultIsPresentedWithoutFailure() {
        AdministrativeAuditView view = new AdministrativeAuditView(
                AdministrativeAuditViewState.loaded(
                        new AuditEventPage(List.of(), 0, 20, 0)));

        assertThat(textOf(view))
                .contains("Nenhum evento administrativo encontrado.")
                .doesNotContain("Não foi possível consultar");
    }

    @Test
    void auditFiltersAndGridUseResponsiveContentSizedLayout() {
        AdministrativeAuditView view = new AdministrativeAuditView(
                AdministrativeAuditViewState.loaded(
                        new AuditEventPage(List.of(), 0, 20, 0)));

        FormLayout form = allComponents(view)
                .filter(FormLayout.class::isInstance)
                .map(FormLayout.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(form.getResponsiveSteps()).hasSize(3);
        assertThat(component(view, Grid.class).isAllRowsVisible()).isTrue();
        assertThat(view.getClassNames())
                .contains("platform-administration", "platform-administration--audit");
    }

    @Test
    void invalidFilterDoesNotCallQueryAndShowsSafeMessage() {
        AdministrativeAuditView view = new AdministrativeAuditView(
                AdministrativeAuditViewState.loaded(
                        new AuditEventPage(List.of(), 0, 20, 0)));
        TextField actorFilter = allComponents(view)
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> "Ator (UUID)".equals(field.getLabel()))
                .findFirst()
                .orElseThrow();
        actorFilter.setValue("not-a-uuid");

        button(view, "Filtrar").click();

        assertThat(textOf(view))
                .contains("Filtro inválido.")
                .doesNotContain("not-a-uuid");
    }

    @Test
    void databaseFailureShowsSafeMessageWithoutExceptionDetails() {
        AdministrativeAuditQueryService queryService = mock(AdministrativeAuditQueryService.class);
        PlatformActorIdentityResolver actorResolver = mock(PlatformActorIdentityResolver.class);
        PlatformAuthorizationService authorization = mock(PlatformAuthorizationService.class);
        when(actorResolver.requireIdentityId(any())).thenReturn(ACTOR_ID);
        when(queryService.findPage(eq(ACTOR_ID), any()))
                .thenThrow(new IllegalStateException("database token operations secret"));

        AdministrativeAuditView view = withAuthentication(() -> new AdministrativeAuditView(
                queryService,
                actorResolver,
                authorization));

        assertThat(textOf(view))
                .contains("Não foi possível consultar a auditoria neste momento.")
                .doesNotContain("database token operations secret", "token", "secret");
    }

    private static AuditEvent event(AuditAction action, AuditResult result) {
        return new AuditEvent(
                UUID.randomUUID(),
                ACTOR_ID,
                action,
                AuditTargetType.TENANT,
                TARGET_ID,
                result,
                Instant.parse("2026-09-22T10:00:00Z"),
                AuditMetadata.empty());
    }

    private static <T> T withAuthentication(Supplier<T> action) {
        Jwt jwt = Jwt.withTokenValue("validated-token")
                .header("alg", "ES256")
                .claim("sub", "subject-a")
                .build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new SupabaseAuthenticationToken(
                jwt,
                ExternalSubject.fromSupabase("subject-a")));
        SecurityContextHolder.setContext(context);
        return action.get();
    }

    private static Button button(AdministrativeAuditView view, String text) {
        return allComponents(view)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Grid<AuditEvent> component(AdministrativeAuditView view, Class<?> type) {
        return (Grid<AuditEvent>) allComponents(view)
                .filter(type::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private static String textOf(AdministrativeAuditView view) {
        return textOfComponent(view);
    }

    private static String textOfComponent(Component component) {
        return component.getElement().getText() + " "
                + component.getChildren()
                        .map(AdministrativeAuditViewIntegrationTests::textOfComponent)
                        .collect(Collectors.joining(" "));
    }

    private static List<Button> buttons(AdministrativeAuditView view) {
        return allComponents(view)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .toList();
    }

    private static java.util.stream.Stream<Component> allComponents(Component component) {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(component),
                component.getChildren().flatMap(AdministrativeAuditViewIntegrationTests::allComponents));
    }
}
