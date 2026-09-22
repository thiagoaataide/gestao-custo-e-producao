package br.com.taas.saas.gestaoproducao.ui.platform;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationDeniedException;
import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.PlatformActorIdentityResolver;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.CreateInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAdministrationView;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationCommandService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationLinkResult;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationNotFoundException;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationProvisioningQueryService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationTenantUnavailableException;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.ResendInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.RevokeInvitationCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.MembershipAdministrationView;
import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.PlatformMembershipAdminService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.PlatformRoleAdministrationView;
import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.RevokeMembershipCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.role.GrantPlatformAdminCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.role.RevokePlatformAdminCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.ChangeTenantStatusCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.CreateTenantCommand;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.TenantCommandValidationException;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.TenantLifecycleAction;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.TenantLifecycleValidationException;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.TenantProvisioningCommandService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.tenant.TenantProvisioningQueryService;
import br.com.taas.saas.gestaoproducao.platform.identity.application.exception.InvitationConflictException;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;

@Route("platform")
@PageTitle("Administração da plataforma | Gestão de Produção")
public final class PlatformAdministrationView extends VerticalLayout {

    private final TenantProvisioningCommandService tenantCommandService;
    private final TenantProvisioningQueryService tenantQueryService;
    private final InvitationCommandService invitationCommandService;
    private final InvitationProvisioningQueryService invitationQueryService;
    private final PlatformMembershipAdminService membershipAdminService;
    private final PlatformActorIdentityResolver actorIdentityResolver;
    private final PlatformAuthorizationService authorizationService;

    private UUID actorIdentityId;
    private PlatformAdministrationViewState state;
    private Grid<Tenant> tenantGrid;
    private Grid<InvitationAdministrationView> invitationGrid;
    private Grid<PlatformRoleAdministrationView> roleGrid;
    private Grid<MembershipAdministrationView> membershipGrid;
    private Span tenantMembershipSummary;
    private ComboBox<Tenant> invitationTenant;
    private EmailField invitationEmail;
    private TextField tenantName;
    private TextField platformAdminIdentity;

    public PlatformAdministrationView(
            TenantProvisioningCommandService tenantCommandService,
            TenantProvisioningQueryService tenantQueryService,
            InvitationCommandService invitationCommandService,
            InvitationProvisioningQueryService invitationQueryService,
            PlatformMembershipAdminService membershipAdminService,
            PlatformActorIdentityResolver actorIdentityResolver,
            PlatformAuthorizationService authorizationService) {
        this.tenantCommandService = Objects.requireNonNull(tenantCommandService);
        this.tenantQueryService = Objects.requireNonNull(tenantQueryService);
        this.invitationCommandService = Objects.requireNonNull(invitationCommandService);
        this.invitationQueryService = Objects.requireNonNull(invitationQueryService);
        this.membershipAdminService = Objects.requireNonNull(membershipAdminService);
        this.actorIdentityResolver = Objects.requireNonNull(actorIdentityResolver);
        this.authorizationService = Objects.requireNonNull(authorizationService);
        this.state = loadState();
        render();
    }

    PlatformAdministrationView(PlatformAdministrationViewState state) {
        this.tenantCommandService = null;
        this.tenantQueryService = null;
        this.invitationCommandService = null;
        this.invitationQueryService = null;
        this.membershipAdminService = null;
        this.actorIdentityResolver = null;
        this.authorizationService = null;
        this.state = Objects.requireNonNull(state);
        render();
    }

    private PlatformAdministrationViewState loadState() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof ExternalSubject subject)) {
                return PlatformAdministrationViewState.denied();
            }
            actorIdentityId = actorIdentityResolver.requireIdentityId(subject);
            authorizationService.requirePlatformAccess(actorIdentityId);
            boolean owner = authorizationService.canManagePlatformRoles(actorIdentityId);
            return new PlatformAdministrationViewState(
                    true,
                    owner,
                    tenantQueryService.listTenants(actorIdentityId),
                    invitationQueryService.listInvitations(actorIdentityId),
                    membershipAdminService.view(actorIdentityId));
        } catch (PlatformAuthorizationDeniedException | IllegalArgumentException exception) {
            return PlatformAdministrationViewState.denied();
        }
    }

    private void render() {
        setWidthFull();
        setMaxWidth("90rem");
        setMargin(true);
        setSpacing(true);

        add(new H1("Administração da plataforma"));
        if (!state.platformAccess()) {
            add(new Paragraph("Acesso não autorizado à administração da plataforma."));
            return;
        }

        add(new Paragraph(
                state.owner()
                        ? "Você está operando como PLATFORM_OWNER."
                        : "Você está operando como PLATFORM_ADMIN."));
        add(tenantSection());
        add(invitationSection());
        add(membershipSection());
        add(platformRoleSection());
    }

    private Component tenantSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H2("Tenants"));

        tenantName = new TextField("Nome do tenant");
        tenantName.setRequired(true);
        Button create = new Button("Criar tenant");
        create.addClickListener(event -> createTenant());
        section.add(new HorizontalLayout(tenantName, create));

        tenantGrid = new Grid<>();
        tenantGrid.setWidthFull();
        tenantGrid.addColumn(Tenant::name).setHeader("Nome");
        tenantGrid.addColumn(tenant -> tenant.status().name()).setHeader("Estado");
        tenantGrid.addColumn(tenant -> activeMembershipTenantIds().contains(tenant.id())
                ? "Com membership ativa"
                : "Sem membership ativa").setHeader("Membership");
        tenantGrid.addColumn(Tenant::createdAt).setHeader("Criado em");
        if (state.owner()) {
            tenantGrid.addComponentColumn(this::tenantLifecycleActions).setHeader("Ações");
        }
        tenantGrid.setItems(state.tenants());
        section.add(tenantGrid);
        long tenantsWithoutActiveMembership = state.tenants().stream()
                .filter(tenant -> !activeMembershipTenantIds().contains(tenant.id()))
                .count();
        tenantMembershipSummary = new Span(
                "Tenants sem membership ativa: " + tenantsWithoutActiveMembership);
        section.add(tenantMembershipSummary);
        return section;
    }

    private Component tenantLifecycleActions(Tenant tenant) {
        HorizontalLayout actions = new HorizontalLayout();
        if (tenant.status() == TenantStatus.CLOSED) {
            Button closed = new Button("Fechado");
            closed.setEnabled(false);
            actions.add(closed);
            return actions;
        }
        TenantLifecycleAction transition = tenant.status() == TenantStatus.ACTIVE
                ? TenantLifecycleAction.SUSPEND
                : TenantLifecycleAction.REACTIVATE;
        Button transitionButton = new Button(
                transition == TenantLifecycleAction.SUSPEND ? "Suspender" : "Reativar");
        transitionButton.addClickListener(event -> changeTenantStatus(tenant, transition));
        Button close = new Button("Fechar");
        close.addClickListener(event -> changeTenantStatus(tenant, TenantLifecycleAction.CLOSE));
        actions.add(transitionButton, close);
        return actions;
    }

    private Component invitationSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H2("Convites"));

        invitationTenant = new ComboBox<>("Tenant");
        invitationTenant.setItemLabelGenerator(Tenant::name);
        invitationTenant.setItems(state.tenants().stream()
                .filter(Tenant::isAvailable)
                .toList());
        invitationTenant.setRequired(true);
        invitationEmail = new EmailField("E-mail do usuário");
        invitationEmail.setRequired(true);
        Button create = new Button("Criar convite");
        create.addClickListener(event -> createInvitation());
        section.add(new HorizontalLayout(invitationTenant, invitationEmail, create));

        invitationGrid = new Grid<>();
        invitationGrid.setWidthFull();
        invitationGrid.addColumn(InvitationAdministrationView::email).setHeader("E-mail");
        invitationGrid.addColumn(view -> tenantName(view.tenantId())).setHeader("Tenant");
        invitationGrid.addColumn(view -> view.status().name()).setHeader("Estado");
        invitationGrid.addColumn(InvitationAdministrationView::expiresAt).setHeader("Expira em");
        invitationGrid.addComponentColumn(this::invitationActions).setHeader("Ações");
        invitationGrid.setItems(state.invitations());
        section.add(invitationGrid);
        section.add(new Span("O envio de e-mail é opcional nesta V0; o link pode ser copiado após criar ou reenviar o convite."));
        return section;
    }

    private Component invitationActions(InvitationAdministrationView invitation) {
        HorizontalLayout actions = new HorizontalLayout();
        if (invitation.status() == InvitationStatus.PENDING
                || invitation.status() == InvitationStatus.EXPIRED) {
            Button resend = new Button("Reenviar");
            resend.addClickListener(event -> resendInvitation(invitation));
            actions.add(resend);
        }
        if (invitation.status() == InvitationStatus.PENDING) {
            Button revoke = new Button("Revogar");
            revoke.addClickListener(event -> revokeInvitation(invitation));
            actions.add(revoke);
        }
        if (actions.getComponentCount() == 0) {
            actions.add(new Span("Sem ações"));
        }
        return actions;
    }

    private Component membershipSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H2("Memberships"));
        membershipGrid = new Grid<>();
        membershipGrid.setWidthFull();
        membershipGrid.addColumn(MembershipAdministrationView::identityId).setHeader("Identity");
        membershipGrid.addColumn(MembershipAdministrationView::tenantId).setHeader("Tenant");
        membershipGrid.addColumn(view -> view.role().value()).setHeader("Papel");
        membershipGrid.addColumn(view -> view.status().name()).setHeader("Estado");
        membershipGrid.addComponentColumn(this::membershipActions).setHeader("Ações");
        membershipGrid.setItems(state.membershipAdministration().memberships());
        section.add(membershipGrid);
        return section;
    }

    private Component membershipActions(MembershipAdministrationView membership) {
        if (membership.status() != MembershipStatus.ACTIVE
                || membership.role() != MembershipRole.TENANT_USER) {
            return new Span("Sem ações");
        }
        Button revoke = new Button("Revogar");
        revoke.addClickListener(event -> revokeMembership(membership));
        return revoke;
    }

    private Component platformRoleSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H2("Papéis de plataforma"));
        if (state.owner()) {
            platformAdminIdentity = new TextField("Identity ID do administrador");
            Button grant = new Button("Conceder PLATFORM_ADMIN");
            grant.addClickListener(event -> grantPlatformAdmin());
            section.add(new HorizontalLayout(platformAdminIdentity, grant));
        }
        roleGrid = new Grid<>();
        roleGrid.setWidthFull();
        roleGrid.addColumn(PlatformRoleAdministrationView::identityId).setHeader("Identity");
        roleGrid.addColumn(view -> view.role().name()).setHeader("Papel");
        roleGrid.addColumn(view -> view.status().name()).setHeader("Estado");
        roleGrid.addColumn(PlatformRoleAdministrationView::createdAt).setHeader("Criado em");
        if (state.owner()) {
            roleGrid.addComponentColumn(this::platformRoleActions).setHeader("Ações");
        }
        roleGrid.setItems(state.membershipAdministration().platformRoles());
        section.add(roleGrid);
        return section;
    }

    private Component platformRoleActions(PlatformRoleAdministrationView role) {
        if (role.role() != PlatformRole.PLATFORM_ADMIN
                || role.status() != PlatformRoleStatus.ACTIVE) {
            return new Span("Sem ações");
        }
        Button revoke = new Button("Revogar");
        revoke.addClickListener(event -> revokePlatformAdmin(role));
        return revoke;
    }

    private void createTenant() {
        if (tenantName.isEmpty()) {
            notifyUser("Informe o nome do tenant.");
            return;
        }
        try {
            tenantCommandService.createTenant(new CreateTenantCommand(
                    actorIdentityId,
                    tenantName.getValue(),
                    Instant.now()));
            tenantName.clear();
            notifyUser("Tenant criado.");
            refresh();
        } catch (TenantCommandValidationException exception) {
            notifyUser("Não foi possível criar o tenant. Verifique o nome informado.");
        } catch (PlatformAuthorizationDeniedException exception) {
            notifyUser("Você não tem permissão para criar tenants.");
        }
    }

    private void changeTenantStatus(Tenant tenant, TenantLifecycleAction action) {
        try {
            tenantCommandService.changeTenantStatus(new ChangeTenantStatusCommand(
                    actorIdentityId,
                    tenant.id(),
                    action,
                    Instant.now()));
            notifyUser(action == TenantLifecycleAction.SUSPEND
                    ? "Tenant suspenso."
                    : "Tenant reativado.");
            refresh();
        } catch (TenantLifecycleValidationException exception) {
            notifyUser("Não foi possível alterar o estado do tenant.");
        } catch (PlatformAuthorizationDeniedException exception) {
            notifyUser("Você não tem permissão para alterar o ciclo de vida.");
        }
    }

    private void createInvitation() {
        if (invitationTenant.isEmpty() || invitationEmail.isEmpty()) {
            notifyUser("Informe o tenant e o e-mail do convite.");
            return;
        }
        try {
            InvitationLinkResult result = invitationCommandService.createInvitation(
                    new CreateInvitationCommand(
                            actorIdentityId,
                            invitationTenant.getValue().id(),
                            invitationEmail.getValue(),
                            Instant.now()));
            showInvitationLink(result, "Convite criado");
            invitationEmail.clear();
            refresh();
        } catch (InvitationTenantUnavailableException exception) {
            notifyUser("O tenant está suspenso ou fechado e não aceita convites.");
        } catch (InvitationConflictException exception) {
            notifyUser("Já existe um convite pendente para este e-mail.");
        } catch (RuntimeException exception) {
            notifyUser("Não foi possível criar o convite. Verifique o e-mail informado.");
        }
    }

    private void resendInvitation(InvitationAdministrationView invitation) {
        try {
            InvitationLinkResult result = invitationCommandService.resendInvitation(
                    new ResendInvitationCommand(actorIdentityId, invitation.id(), Instant.now()));
            showInvitationLink(result, "Convite reenviado");
            refresh();
        } catch (InvitationTenantUnavailableException exception) {
            notifyUser("O tenant está suspenso ou fechado e não aceita convites.");
        } catch (InvitationNotFoundException exception) {
            notifyUser("O convite não está mais disponível.");
        } catch (RuntimeException exception) {
            notifyUser("Não foi possível reenviar o convite. Verifique o estado atual.");
        }
    }

    private void revokeInvitation(InvitationAdministrationView invitation) {
        try {
            invitationCommandService.revokeInvitation(
                    new RevokeInvitationCommand(actorIdentityId, invitation.id(), Instant.now()));
            notifyUser("Convite revogado.");
            refresh();
        } catch (InvitationNotFoundException exception) {
            notifyUser("O convite não está mais disponível.");
        } catch (RuntimeException exception) {
            notifyUser("Não foi possível revogar o convite. Verifique o estado atual.");
        }
    }

    private void revokeMembership(MembershipAdministrationView membership) {
        try {
            membershipAdminService.revokeMembership(
                    new RevokeMembershipCommand(actorIdentityId, membership.id(), Instant.now()));
            notifyUser("Membership revogada.");
            refresh();
        } catch (RuntimeException exception) {
            notifyUser("Não foi possível revogar a membership. Verifique o estado atual.");
        }
    }

    private void grantPlatformAdmin() {
        try {
            UUID targetIdentityId = UUID.fromString(platformAdminIdentity.getValue().trim());
            membershipAdminService.grantPlatformAdmin(
                    new GrantPlatformAdminCommand(actorIdentityId, targetIdentityId, Instant.now()));
            platformAdminIdentity.clear();
            notifyUser("PLATFORM_ADMIN concedido.");
            refresh();
        } catch (IllegalArgumentException exception) {
            notifyUser("Informe um Identity ID válido.");
        } catch (RuntimeException exception) {
            notifyUser("Não foi possível conceder o papel de plataforma.");
        }
    }

    private void revokePlatformAdmin(PlatformRoleAdministrationView role) {
        try {
            membershipAdminService.revokePlatformAdmin(
                    new RevokePlatformAdminCommand(actorIdentityId, role.id(), Instant.now()));
            notifyUser("PLATFORM_ADMIN revogado.");
            refresh();
        } catch (RuntimeException exception) {
            notifyUser("Não foi possível revogar o papel de plataforma.");
        }
    }

    private void showInvitationLink(InvitationLinkResult result, String title) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        TextArea link = new TextArea("Link do convite");
        link.setWidthFull();
        link.setReadOnly(true);
        link.setValue(result.link());
        Button copy = new Button("Copiar link");
        copy.addClickListener(event -> {
            link.getElement().executeJs("navigator.clipboard.writeText($0)", result.link());
            notifyUser("Link copiado.");
        });
        Button close = new Button("Fechar", event -> dialog.close());
        dialog.add(new Paragraph(
                "Se o e-mail não for enviado, encaminhe este link ao usuário por um canal seguro."));
        dialog.add(link);
        dialog.getFooter().add(copy, close);
        dialog.open();
    }

    private void refresh() {
        state = new PlatformAdministrationViewState(
                true,
                state.owner(),
                tenantQueryService.listTenants(actorIdentityId),
                invitationQueryService.listInvitations(actorIdentityId),
                membershipAdminService.view(actorIdentityId));
        tenantGrid.setItems(state.tenants());
        invitationTenant.setItems(state.tenants().stream().filter(Tenant::isAvailable).toList());
        invitationGrid.setItems(state.invitations());
        membershipGrid.setItems(state.membershipAdministration().memberships());
        roleGrid.setItems(state.membershipAdministration().platformRoles());
        updateTenantMembershipSummary();
    }

    private Set<UUID> activeMembershipTenantIds() {
        return state.membershipAdministration().memberships().stream()
                .filter(view -> view.status() == MembershipStatus.ACTIVE)
                .map(MembershipAdministrationView::tenantId)
                .collect(Collectors.toSet());
    }

    private String tenantName(UUID tenantId) {
        return state.tenants().stream()
                .filter(tenant -> tenant.id().equals(tenantId))
                .map(Tenant::name)
                .findFirst()
                .orElse(tenantId.toString());
    }

    private void updateTenantMembershipSummary() {
        if (tenantMembershipSummary == null) {
            return;
        }
        long tenantsWithoutActiveMembership = state.tenants().stream()
                .filter(tenant -> !activeMembershipTenantIds().contains(tenant.id()))
                .count();
        tenantMembershipSummary.setText(
                "Tenants sem membership ativa: " + tenantsWithoutActiveMembership);
    }

    private void notifyUser(String message) {
        Notification.show(message, 5_000, Position.MIDDLE);
    }
}
