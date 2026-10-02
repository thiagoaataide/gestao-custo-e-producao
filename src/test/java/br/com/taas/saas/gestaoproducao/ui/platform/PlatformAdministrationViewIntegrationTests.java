package br.com.taas.saas.gestaoproducao.ui.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.tabs.TabSheet;

import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.CreateInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAdministrationView;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationCommandService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationLinkResult;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationProvisioningQueryService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.MembershipAdministrationView;
import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.PlatformMembershipAdminService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.PlatformMembershipAdministrationView;
import br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment.AssignOperationalManagerCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment.OperationalManagerAssignmentService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment.OperationalManagerAssignmentView;
import br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment.RevokeOperationalManagerCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.domain.model.operationalassignment.OperationalManagerAssignment;
import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationDeniedException;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.TenantProvisioningQueryService;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Invitation;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationTokenDigest;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;

@SpringBootTest
@ActiveProfiles("test")
class PlatformAdministrationViewIntegrationTests {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID IDENTITY_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBERSHIP_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID INVITATION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-09-22T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-23T10:00:00Z");

    @Test
    void membersAndInvitationsShareOneMembersTab() {
        PlatformAdministrationView view = new PlatformAdministrationView(state(
                List.of(tenant(TENANT_ID, "Tenant")), List.of(), List.of()));

        TabSheet tabs = component(view, TabSheet.class);
        assertThat(tabs.getTabCount()).isEqualTo(3);
        assertThat(java.util.stream.IntStream.range(0, tabs.getTabCount())
                .mapToObj(index -> tabs.getTabAt(index).getLabel())
                .toList()).containsExactly("Tenants", "Membros", "Papéis da plataforma");

        tabs.setSelectedIndex(1);
        button(view, "Adicionar membro").click();

        assertThat(tabs.getSelectedIndex()).isEqualTo(1);
        assertThat(component(view, EmailField.class).isVisible()).isTrue();
        assertThat(textOf(view)).doesNotContain("Convites</");
    }

    @Test
    void membersListCombinesMembershipsAndInvitationsWithTenantAndStatusFilters() {
        Tenant tenant = tenant(TENANT_ID, "Tenant A");
        Tenant otherTenant = tenant(OTHER_TENANT_ID, "Tenant B");
        MembershipAdministrationView active = membership(MEMBERSHIP_ID, IDENTITY_ID, TENANT_ID,
                MembershipStatus.ACTIVE);
        InvitationAdministrationView accepted = invitation(INVITATION_ID, TENANT_ID,
                "active@example.com", InvitationStatus.ACCEPTED, IDENTITY_ID);
        InvitationAdministrationView pendingA = invitation(UUID.randomUUID(), TENANT_ID,
                "pending-a@example.com", InvitationStatus.PENDING, null);
        InvitationAdministrationView pendingB = invitation(UUID.randomUUID(), OTHER_TENANT_ID,
                "pending-b@example.com", InvitationStatus.PENDING, null);
        InvitationAdministrationView expired = invitation(UUID.randomUUID(), OTHER_TENANT_ID,
                "expired@example.com", InvitationStatus.EXPIRED, null);
        InvitationAdministrationView revoked = invitation(UUID.randomUUID(), TENANT_ID,
                "revoked@example.com", InvitationStatus.REVOKED, null);
        MembershipAdministrationView identityWithoutEmail = membership(
                UUID.randomUUID(), UUID.fromString("10000000-0000-0000-0000-000000000099"),
                OTHER_TENANT_ID, MembershipStatus.ACTIVE);
        PlatformAdministrationView view = new PlatformAdministrationView(state(
                List.of(tenant, otherTenant), List.of(accepted, pendingA, pendingB, expired, revoked),
                List.of(active, identityWithoutEmail)));

        List<Component> cards = memberCards(view);
        assertThat(cards).hasSize(6);
        assertThat(textOf(view)).contains("active@example.com", "Acesso ativo", "Convite pendente",
                "Convite expirado", "Convite revogado");
        assertThat(occurrences(textOf(view), "active@example.com")).isEqualTo(1);
        assertThat(textOf(view)).contains(identityWithoutEmail.identityId().toString());

        setComboByLabel(view, "Tenant", "Tenant A");
        setComboByLabel(view, "Situação", "Convites pendentes");
        assertThat(memberCards(view)).hasSize(1);
        assertThat(textOf(view)).contains("pending-a@example.com").doesNotContain("pending-b@example.com");

        setComboByLabel(view, "Tenant", "Tenant B");
        assertThat(memberCards(view)).hasSize(1);
        assertThat(textOf(view)).contains("pending-b@example.com").doesNotContain("pending-a@example.com");

        setComboByLabel(view, "Situação", "Convites expirados");
        assertThat(memberCards(view)).hasSize(1);
        assertThat(textOf(view)).contains("expired@example.com");
    }

    @Test
    void invitingFromMembersKeepsPendingInvitationInline() {
        UI ui = new UI();
        UI.setCurrent(ui);
        Tenant tenant = tenant(TENANT_ID, "Tenant A");
        InvitationCommandService commandService = mock(InvitationCommandService.class);
        InvitationProvisioningQueryService invitationQuery = mock(InvitationProvisioningQueryService.class);
        TenantProvisioningQueryService tenantQuery = mock(TenantProvisioningQueryService.class);
        PlatformMembershipAdminService membershipAdmin = mock(PlatformMembershipAdminService.class);
        InvitationAdministrationView pendingView = invitation(INVITATION_ID, TENANT_ID,
                "new@example.com", InvitationStatus.PENDING, null);
        Invitation created = domainInvitation(INVITATION_ID, "new@example.com", null, InvitationStatus.PENDING);
        List<InvitationAdministrationView> invitations = new ArrayList<>();
        when(commandService.createInvitation(any(CreateInvitationCommand.class)))
                .thenReturn(new InvitationLinkResult(created, "https://app.example/invitations/secret"));
        when(invitationQuery.listInvitations(ACTOR_ID)).thenAnswer(ignored -> List.copyOf(invitations));
        when(tenantQuery.listTenants(ACTOR_ID)).thenReturn(List.of(tenant));
        when(membershipAdmin.view(ACTOR_ID)).thenAnswer(ignored ->
                new PlatformMembershipAdministrationView(List.of(), List.of()));
        PlatformAdministrationView view = new PlatformAdministrationView(
                state(List.of(tenant), List.of(), List.of()), ACTOR_ID,
                tenantQuery, commandService, invitationQuery, membershipAdmin);
        ui.add(view);
        when(commandService.createInvitation(any(CreateInvitationCommand.class))).thenAnswer(invocation -> {
            invitations.add(pendingView);
            return new InvitationLinkResult(created, "https://app.example/invitations/secret");
        });

        TabSheet tabs = component(view, TabSheet.class);
        tabs.setSelectedIndex(1);
        button(view, "Adicionar membro").click();
        ComboBox<?> tenantField = combo(view, "Tenant", false);
        selectComboLabel(tenantField, "Tenant A");
        EmailField email = (EmailField) allComponents(view)
                .filter(EmailField.class::isInstance)
                .map(EmailField.class::cast)
                .findFirst().orElseThrow();
        email.setValue("new@example.com");
        button(view, "Criar convite").click();

        assertThat(tabs.getSelectedIndex()).isEqualTo(1);
        assertThat(textOf(view)).contains("new@example.com", "Convite pendente")
                .doesNotContain("Acesso ativo");
        TextArea link = component(view.lastInvitationLinkDialog(), TextArea.class);
        assertThat(link.getValue()).isEqualTo("https://app.example/invitations/secret");
        assertThat(memberCards(view)).hasSize(1);
        verify(membershipAdmin, never()).revokeMembership(any());
        verify(commandService).createInvitation(any(CreateInvitationCommand.class));
    }

    @Test
    void revocationRequiresConfirmationAndRefreshesMembersList() {
        UI ui = new UI();
        UI.setCurrent(ui);
        Tenant tenant = tenant(TENANT_ID, "Tenant A");
        InvitationCommandService commandService = mock(InvitationCommandService.class);
        InvitationProvisioningQueryService invitationQuery = mock(InvitationProvisioningQueryService.class);
        TenantProvisioningQueryService tenantQuery = mock(TenantProvisioningQueryService.class);
        PlatformMembershipAdminService membershipAdmin = mock(PlatformMembershipAdminService.class);
        InvitationAdministrationView pending = invitation(INVITATION_ID, TENANT_ID,
                "pending@example.com", InvitationStatus.PENDING, null);
        InvitationAdministrationView revoked = invitation(INVITATION_ID, TENANT_ID,
                "pending@example.com", InvitationStatus.REVOKED, null);
        InvitationAdministrationView accepted = invitation(UUID.randomUUID(), TENANT_ID,
                "member@example.com", InvitationStatus.ACCEPTED, IDENTITY_ID);
        MembershipAdministrationView active = membership(MEMBERSHIP_ID, IDENTITY_ID, TENANT_ID,
                MembershipStatus.ACTIVE);
        MembershipAdministrationView revokedMembership = membership(MEMBERSHIP_ID, IDENTITY_ID,
                TENANT_ID, MembershipStatus.REVOKED);
        AtomicBoolean invitationWasRevoked = new AtomicBoolean();
        AtomicBoolean membershipWasRevoked = new AtomicBoolean();
        when(invitationQuery.listInvitations(ACTOR_ID)).thenAnswer(ignored ->
                List.of(invitationWasRevoked.get() ? revoked : pending, accepted));
        when(tenantQuery.listTenants(ACTOR_ID)).thenReturn(List.of(tenant));
        when(membershipAdmin.view(ACTOR_ID)).thenAnswer(ignored -> new PlatformMembershipAdministrationView(
                List.of(), List.of(membershipWasRevoked.get() ? revokedMembership : active)));
        when(commandService.revokeInvitation(any())).thenAnswer(invocation -> {
            invitationWasRevoked.set(true);
            return null;
        });
        when(membershipAdmin.revokeMembership(any())).thenAnswer(invocation -> {
            membershipWasRevoked.set(true);
            return null;
        });
        PlatformAdministrationView view = new PlatformAdministrationView(
                state(List.of(tenant), List.of(pending, accepted), List.of(active)), ACTOR_ID,
                tenantQuery, commandService, invitationQuery, membershipAdmin);
        ui.add(view);
        component(view, TabSheet.class).setSelectedIndex(1);

        button(view, "Revogar convite").click();
        assertThat(view.pendingRevocationDialog()).isNotNull();
        assertThat(textOf(view.pendingRevocationDialog())).contains("pending@example.com", "Tenant A");
        dialogButton(view.pendingRevocationDialog(), "Cancelar").click();
        assertThat(textOf(view)).contains("Convite pendente");
        verify(commandService, never()).revokeInvitation(any());

        button(view, "Revogar convite").click();
        dialogButton(view.pendingRevocationDialog(), "Revogar convite").click();
        verify(commandService).revokeInvitation(any());
        assertThat(textOf(view)).contains("Convite revogado").doesNotContain("Convite pendente");

        button(view, "Revogar acesso").click();
        dialogButton(view.pendingRevocationDialog(), "Cancelar").click();
        verify(membershipAdmin, never()).revokeMembership(any());
        assertThat(textOf(view)).contains("Acesso ativo");

        button(view, "Revogar acesso").click();
        dialogButton(view.pendingRevocationDialog(), "Revogar acesso").click();
        verify(membershipAdmin).revokeMembership(any());
        assertThat(textOf(view)).contains("Acesso revogado").doesNotContain("Acesso ativo");
    }

    @Test
    void formsAndGridsUseResponsiveAndContentSizedLayouts() {
        PlatformAdministrationView view = new PlatformAdministrationView(state(
                List.of(tenant(TENANT_ID, "Tenant")), List.of(), List.of()));

        FormLayout form = component(view, FormLayout.class);
        assertThat(form.getResponsiveSteps()).hasSize(3);
        assertThat(allComponents(view)
                .filter(com.vaadin.flow.component.grid.Grid.class::isInstance)
                .map(com.vaadin.flow.component.grid.Grid.class::cast)
                .allMatch(com.vaadin.flow.component.grid.Grid::isAllRowsVisible)).isTrue();
        assertThat(component(view, Anchor.class).getHref()).isEqualTo("/platform/audit");
    }

    @Test
    void tenantUserDoesNotSeePlatformCommandsWhenRouteIsReachedDirectly() {
        PlatformAdministrationView view = new PlatformAdministrationView(
                PlatformAdministrationViewState.denied());

        assertThat(textOf(view))
                .contains("Acesso não autorizado")
                .doesNotContain("Criar tenant", "Criar convite", "Adicionar membro", "Revogar", "Membros");
        assertThat(allComponents(view).filter(Button.class::isInstance)).isEmpty();
    }

    @Test
    void commonPlatformAdminSeesPlatformReadOperationsWithoutOwnerControls() {
        PlatformAdministrationView view = new PlatformAdministrationView(new PlatformAdministrationViewState(
                true,
                false,
                List.of(),
                List.of(),
                new PlatformMembershipAdministrationView(List.of(), List.of())));

        assertThat(textOf(view))
                .contains("PLATFORM_ADMIN", "Criar tenant", "Adicionar membro", "Membros")
                .doesNotContain("Conceder PLATFORM_ADMIN");
    }

    @Test
    void activeMembershipCanBeAssignedAsOperationalManagerAndStatusRefreshes() {
        UI ui = new UI();
        UI.setCurrent(ui);
        Tenant tenant = tenant(TENANT_ID, "Tenant A");
        MembershipAdministrationView member = membership(MEMBERSHIP_ID, IDENTITY_ID, TENANT_ID,
                MembershipStatus.ACTIVE);
        OperationalManagerAssignmentService assignments = mock(OperationalManagerAssignmentService.class);
        when(assignments.list(ACTOR_ID)).thenReturn(List.of());
        OperationalManagerAssignment assigned = OperationalManagerAssignment.assign(
                UUID.randomUUID(), TENANT_ID, MEMBERSHIP_ID, ACTOR_ID, CREATED_AT.plusSeconds(1));
        when(assignments.assign(any(AssignOperationalManagerCommand.class))).thenAnswer(invocation -> {
            when(assignments.list(ACTOR_ID)).thenReturn(List.of(assignmentView(assigned)));
            return assigned;
        });
        PlatformAdministrationView view = platformViewWithAssignments(tenant, member, assignments);
        ui.add(view);
        component(view, TabSheet.class).setSelectedIndex(1);

        assertThat(textOf(view)).contains("Responsável operacional: não designado");
        button(view, "Designar responsável operacional").click();

        verify(assignments).assign(any(AssignOperationalManagerCommand.class));
        assertThat(textOf(view)).contains("Responsável operacional: ativo")
                .doesNotContain("Responsável operacional: não designado");
    }

    @Test
    void activeOperationalAssignmentCanBeRevokedAndRemainsVisibleAsRevoked() {
        UI ui = new UI();
        UI.setCurrent(ui);
        MembershipAdministrationView member = membership(MEMBERSHIP_ID, IDENTITY_ID, TENANT_ID,
                MembershipStatus.ACTIVE);
        OperationalManagerAssignmentService assignments = mock(OperationalManagerAssignmentService.class);
        OperationalManagerAssignment active = OperationalManagerAssignment.assign(
                UUID.randomUUID(), TENANT_ID, MEMBERSHIP_ID, ACTOR_ID, CREATED_AT);
        when(assignments.list(ACTOR_ID)).thenReturn(List.of(assignmentView(active)));
        OperationalManagerAssignment revoked = OperationalManagerAssignment.assign(
                active.id(), TENANT_ID, MEMBERSHIP_ID, ACTOR_ID, CREATED_AT);
        revoked.revoke(ACTOR_ID, CREATED_AT.plusSeconds(1));
        when(assignments.revoke(any(RevokeOperationalManagerCommand.class))).thenAnswer(invocation -> {
            when(assignments.list(ACTOR_ID)).thenReturn(List.of(assignmentView(revoked)));
            return revoked;
        });
        PlatformAdministrationView view = platformViewWithAssignments(
                tenant(TENANT_ID, "Tenant A"), member, assignments, List.of(active));
        ui.add(view);
        component(view, TabSheet.class).setSelectedIndex(1);

        assertThat(textOf(view)).contains("Responsável operacional: ativo");
        button(view, "Revogar responsável operacional").click();

        verify(assignments).revoke(any(RevokeOperationalManagerCommand.class));
        assertThat(textOf(view)).contains("Responsável operacional: revogado")
                .doesNotContain("Responsável operacional: ativo");
    }

    @Test
    void deniedOperationalAssignmentShowsAccessDeniedMessage() {
        UI ui = new UI();
        UI.setCurrent(ui);
        MembershipAdministrationView member = membership(MEMBERSHIP_ID, IDENTITY_ID, TENANT_ID,
                MembershipStatus.ACTIVE);
        OperationalManagerAssignmentService assignments = mock(OperationalManagerAssignmentService.class);
        when(assignments.list(ACTOR_ID)).thenReturn(List.of());
        doThrow(new PlatformAuthorizationDeniedException())
                .when(assignments).assign(any(AssignOperationalManagerCommand.class));
        PlatformAdministrationView view = platformViewWithAssignments(
                tenant(TENANT_ID, "Tenant A"), member, assignments);
        ui.add(view);
        component(view, TabSheet.class).setSelectedIndex(1);

        try (var notifications = mockStatic(com.vaadin.flow.component.notification.Notification.class)) {
            button(view, "Designar responsável operacional").click();
            notifications.verify(() -> com.vaadin.flow.component.notification.Notification.show(
                    "Acesso negado: você não tem permissão para alterar a designação.", 5_000,
                    com.vaadin.flow.component.notification.Notification.Position.MIDDLE));
        }
        verify(assignments).assign(any(AssignOperationalManagerCommand.class));
    }

    private static PlatformAdministrationViewState state(
            List<Tenant> tenants,
            List<InvitationAdministrationView> invitations,
            List<MembershipAdministrationView> memberships) {
        return new PlatformAdministrationViewState(true, true, tenants, invitations,
                new PlatformMembershipAdministrationView(List.of(), memberships));
    }

    private static PlatformAdministrationView platformViewWithAssignments(
            Tenant tenant,
            MembershipAdministrationView membership,
            OperationalManagerAssignmentService assignments) {
        return platformViewWithAssignments(tenant, membership, assignments, List.of());
    }

    private static OperationalManagerAssignmentView assignmentView(OperationalManagerAssignment assignment) {
        return new OperationalManagerAssignmentView(assignment.id(), assignment.membershipId(),
                assignment.status().name(), assignment.assignedAt());
    }

    private static PlatformAdministrationView platformViewWithAssignments(
            Tenant tenant,
            MembershipAdministrationView membership,
            OperationalManagerAssignmentService assignments,
            List<OperationalManagerAssignment> initialAssignments) {
        TenantProvisioningQueryService tenantQuery = mock(TenantProvisioningQueryService.class);
        InvitationCommandService invitationCommands = mock(InvitationCommandService.class);
        InvitationProvisioningQueryService invitationQuery = mock(InvitationProvisioningQueryService.class);
        PlatformMembershipAdminService memberships = mock(PlatformMembershipAdminService.class);
        when(tenantQuery.listTenants(ACTOR_ID)).thenReturn(List.of(tenant));
        when(invitationQuery.listInvitations(ACTOR_ID)).thenReturn(List.of());
        when(memberships.view(ACTOR_ID)).thenReturn(new PlatformMembershipAdministrationView(
                List.of(), List.of(membership)));
        PlatformAdministrationViewState state = new PlatformAdministrationViewState(
                true, true, List.of(tenant), List.of(),
                new PlatformMembershipAdministrationView(List.of(), List.of(membership)),
                initialAssignments.stream().map(PlatformAdministrationViewIntegrationTests::assignmentView).toList());
        return new PlatformAdministrationView(
                state, ACTOR_ID,
                tenantQuery, invitationCommands, invitationQuery, memberships, assignments);
    }

    private static Tenant tenant(UUID id, String name) {
        return new Tenant(id, name, TenantStatus.ACTIVE, CREATED_AT);
    }

    private static MembershipAdministrationView membership(
            UUID id, UUID identityId, UUID tenantId, MembershipStatus status) {
        return new MembershipAdministrationView(id, identityId, tenantId, status,
                MembershipRole.TENANT_USER, CREATED_AT, status == MembershipStatus.REVOKED ? CREATED_AT : null);
    }

    private static InvitationAdministrationView invitation(
            UUID id, UUID tenantId, String email, InvitationStatus status, UUID identityId) {
        return new InvitationAdministrationView(id, tenantId, email, MembershipRole.TENANT_USER,
                status, identityId, EXPIRES_AT,
                status == InvitationStatus.ACCEPTED ? CREATED_AT : null,
                status == InvitationStatus.REVOKED ? CREATED_AT : null,
                ACTOR_ID, CREATED_AT);
    }

    private static Invitation domainInvitation(
            UUID id, String email, UUID identityId, InvitationStatus status) {
        return new Invitation(id, TENANT_ID, NormalizedEmail.from(email), MembershipRole.TENANT_USER,
                status, InvitationTokenDigest.fromToken("test-token"), identityId, EXPIRES_AT,
                null, null, ACTOR_ID, CREATED_AT);
    }

    private static int occurrences(String text, String token) {
        return (text.length() - text.replace(token, "").length()) / token.length();
    }

    private static List<Component> memberCards(PlatformAdministrationView view) {
        return allComponents(view)
                .filter(component -> component.getClassNames().contains("platform-administration__member-card"))
                .toList();
    }

    private static void setComboByLabel(PlatformAdministrationView view, String comboLabel, String itemLabel) {
        ComboBox<?> field = combo(view, comboLabel, comboLabel.equals("Tenant"));
        selectComboLabel(field, itemLabel);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void selectComboLabel(ComboBox<?> combo, String label) {
        ComboBox raw = combo;
        Object matchingItem = ((java.util.Collection<?>) raw.getListDataView().getItems().toList()).stream()
                .filter(item -> label.equals(raw.getItemLabelGenerator().apply(item)))
                .findFirst().orElseThrow();
        raw.setValue(matchingItem);
    }

    private static ComboBox<?> combo(PlatformAdministrationView view, String label, boolean firstMatch) {
        List<? extends ComboBox<?>> matches = allComponents(view)
                .filter(ComboBox.class::isInstance)
                .map(field -> (ComboBox<?>) field)
                .filter(field -> label.equals(field.getLabel()))
                .toList();
        return firstMatch ? matches.getFirst() : matches.getLast();
    }

    private static Button button(Component component, String text) {
        return allComponents(component)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Button not found: " + text));
    }

    private static Button dialogButton(Dialog dialog, String text) {
        return dialog.getFooter().getElement().getChildren()
                .map(element -> element.getComponent().orElse(null))
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Dialog button not found: " + text));
    }
    private static <T extends Component> T component(Component root, Class<T> type) {
        return allComponents(root).filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static String textOf(Component component) {
        return component.getElement().getText() + " "
                + childComponents(component).map(PlatformAdministrationViewIntegrationTests::textOf)
                        .collect(Collectors.joining(" "));
    }

    private static java.util.stream.Stream<Component> allComponents(Component component) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(component),
                childComponents(component).flatMap(PlatformAdministrationViewIntegrationTests::allComponents));
    }

    private static java.util.stream.Stream<Component> childComponents(Component component) {
        if (component instanceof TabSheet tabs) {
            return java.util.stream.IntStream.range(0, tabs.getTabCount())
                    .mapToObj(tabs::getTabAt)
                    .map(tabs::getComponent);
        }
        return component.getChildren();
    }
}
