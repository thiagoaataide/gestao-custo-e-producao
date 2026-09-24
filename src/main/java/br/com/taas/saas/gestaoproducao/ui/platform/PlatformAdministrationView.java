package br.com.taas.saas.gestaoproducao.ui.platform;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.component.tabs.TabSheet;

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
import br.com.taas.saas.gestaoproducao.ui.platform.audit.AdministrativeAuditView;

@Route("platform")
@PageTitle("Administração da plataforma | Gestão de Produção")
@PermitAll
@StyleSheet("css/platform-administration.css")
public final class PlatformAdministrationView extends VerticalLayout {

    private final TenantProvisioningCommandService tenantCommandService;
    private final TenantProvisioningQueryService tenantQueryService;
    private final InvitationCommandService invitationCommandService;
    private final InvitationProvisioningQueryService invitationQueryService;
    private final PlatformMembershipAdminService membershipAdminService;
    private final PlatformActorIdentityResolver actorIdentityResolver;
    private final PlatformAuthorizationService authorizationService;
    private final AuthenticationContext authenticationContext;

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
    private TabSheet administrationTabs;

    @Autowired
    public PlatformAdministrationView(
            TenantProvisioningCommandService tenantCommandService,
            TenantProvisioningQueryService tenantQueryService,
            InvitationCommandService invitationCommandService,
            InvitationProvisioningQueryService invitationQueryService,
            PlatformMembershipAdminService membershipAdminService,
            PlatformActorIdentityResolver actorIdentityResolver,
            PlatformAuthorizationService authorizationService,
            AuthenticationContext authenticationContext) {
        this.tenantCommandService = Objects.requireNonNull(tenantCommandService);
        this.tenantQueryService = Objects.requireNonNull(tenantQueryService);
        this.invitationCommandService = Objects.requireNonNull(invitationCommandService);
        this.invitationQueryService = Objects.requireNonNull(invitationQueryService);
        this.membershipAdminService = Objects.requireNonNull(membershipAdminService);
        this.actorIdentityResolver = Objects.requireNonNull(actorIdentityResolver);
        this.authorizationService = Objects.requireNonNull(authorizationService);
        this.authenticationContext = Objects.requireNonNull(authenticationContext);
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
        this.authenticationContext = null;
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
        addClassNames("platform-administration", "platform-administration--main");
        setWidthFull();
        setMaxWidth("90rem");
        setMargin(true);
        setSpacing(true);

        FlexLayout heading = new FlexLayout();
        heading.add(new H1("Administração da plataforma"));
        heading.add(new Anchor("/platform/audit", "Auditoria"));
        if (authenticationContext != null) {
            heading.add(new Button("Sair", event -> authenticationContext.logout()));
        }
        heading.setWidthFull();
        heading.setAlignItems(Alignment.CENTER);
        heading.setJustifyContentMode(FlexLayout.JustifyContentMode.BETWEEN);
        heading.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        heading.addClassName("platform-administration__header");
        add(heading);
        if (!state.platformAccess()) {
            add(new Paragraph("Acesso não autorizado à administração da plataforma."));
            return;
        }

        add(new Paragraph(
                state.owner()
                        ? "Você está operando como PLATFORM_OWNER."
                        : "Você está operando como PLATFORM_ADMIN."));
        administrationTabs = new TabSheet();
        administrationTabs.setWidthFull();
        administrationTabs.addClassName("platform-administration__tabs");
        administrationTabs.add("Tenants", tenantSection());
        administrationTabs.add("Convites", invitationSection());
        administrationTabs.add("Membros", membershipSection());
        administrationTabs.add("Papéis da plataforma", platformRoleSection());
        add(administrationTabs);
    }

    private Component tenantSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.addClassName("platform-administration__panel");
        section.add(new H2("Tenants"));

        tenantName = new TextField("Nome do tenant");
        tenantName.setRequired(true);
        Button create = new Button("Criar tenant");
        stylePrimaryAction(create);
        create.addClickListener(event -> createTenant());
        section.add(responsiveForm(tenantName, create));

        tenantGrid = new Grid<>();
        styleGrid(tenantGrid);
        tenantGrid.addColumn(Tenant::name).setHeader("Nome do tenant");
        tenantGrid.addColumn(tenant -> tenant.status().name()).setHeader("Estado");
        tenantGrid.addColumn(tenant -> activeMembershipTenantIds().contains(tenant.id())
                ? "Com membro ativo"
                : "Sem membro ativo").setHeader("Vínculo");
        tenantGrid.addColumn(Tenant::createdAt).setHeader("Criado em");
        if (state.owner()) {
            tenantGrid.addComponentColumn(this::tenantLifecycleActions).setHeader("Ações");
        }
        tenantGrid.setItems(state.tenants());
        section.add(tenantGrid);
        tenantMembershipSummary = new Span();
        tenantMembershipSummary.addClassName("platform-administration__summary");
        updateTenantMembershipSummary();
        section.add(tenantMembershipSummary);
        return section;
    }

    private Component tenantLifecycleActions(Tenant tenant) {
        FlexLayout actions = actionLayout();
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
        close.addThemeVariants(ButtonVariant.AURA_DANGER);
        close.addClickListener(event -> changeTenantStatus(tenant, TenantLifecycleAction.CLOSE));
        actions.add(transitionButton, close);
        return actions;
    }

    private Component invitationSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.addClassName("platform-administration__panel");
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
        stylePrimaryAction(create);
        create.addClickListener(event -> createInvitation());
        section.add(responsiveForm(invitationTenant, invitationEmail, create));

        invitationGrid = new Grid<>();
        styleGrid(invitationGrid);
        invitationGrid.addColumn(InvitationAdministrationView::email).setHeader("E-mail");
        invitationGrid.addColumn(view -> tenantName(view.tenantId())).setHeader("Tenant");
        invitationGrid.addColumn(view -> view.status().name()).setHeader("Estado");
        invitationGrid.addColumn(InvitationAdministrationView::expiresAt).setHeader("Expira em");
        invitationGrid.addComponentColumn(this::invitationActions).setHeader("Ações");
        invitationGrid.setItems(state.invitations());
        section.add(invitationGrid);
        Span guidance = new Span(
                "O envio de e-mail é opcional nesta V0; copie o link para compartilhá-lo por um canal seguro.");
        guidance.addClassName("platform-administration__hint");
        section.add(guidance);
        return section;
    }

    private Component invitationActions(InvitationAdministrationView invitation) {
        FlexLayout actions = actionLayout();
        if (invitation.status() == InvitationStatus.PENDING
                || invitation.status() == InvitationStatus.EXPIRED) {
            Button resend = new Button("Reenviar");
            resend.addClickListener(event -> resendInvitation(invitation));
            actions.add(resend);
        }
        if (invitation.status() == InvitationStatus.PENDING) {
            Button revoke = new Button("Revogar");
            revoke.addThemeVariants(ButtonVariant.AURA_DANGER);
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
        section.addClassName("platform-administration__panel");
        section.add(new H2("Membros"));
        section.add(new Paragraph(
                "O vínculo é criado quando o usuário aceita um convite. Para incluir alguém, envie um convite ao tenant."));
        Button inviteMember = new Button("Convidar membro", event -> {
            administrationTabs.setSelectedIndex(1);
            invitationEmail.focus();
        });
        stylePrimaryAction(inviteMember);
        inviteMember.addClassName("platform-administration__member-invite");
        section.add(inviteMember);
        membershipGrid = new Grid<>();
        styleGrid(membershipGrid);
        membershipGrid.addColumn(MembershipAdministrationView::identityId).setHeader("Identidade");
        membershipGrid.addColumn(view -> tenantName(view.tenantId())).setHeader("Tenant");
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
        revoke.addThemeVariants(ButtonVariant.AURA_DANGER);
        revoke.addClickListener(event -> revokeMembership(membership));
        return revoke;
    }

    private Component platformRoleSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.addClassName("platform-administration__panel");
        section.add(new H2("Papéis da plataforma"));
        if (state.owner()) {
            platformAdminIdentity = new TextField("ID da identidade do administrador");
            platformAdminIdentity.setRequired(true);
            Button grant = new Button("Conceder PLATFORM_ADMIN");
            stylePrimaryAction(grant);
            grant.addClickListener(event -> grantPlatformAdmin());
            section.add(responsiveForm(platformAdminIdentity, grant));
        }
        roleGrid = new Grid<>();
        styleGrid(roleGrid);
        roleGrid.addColumn(PlatformRoleAdministrationView::identityId).setHeader("Identidade");
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
        revoke.addThemeVariants(ButtonVariant.AURA_DANGER);
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
        copy.addThemeVariants(ButtonVariant.AURA_PRIMARY);
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
                "Tenants sem membro ativo: " + tenantsWithoutActiveMembership);
    }

    private static FormLayout responsiveForm(Component... controls) {
        FormLayout form = new FormLayout();
        form.addClassName("platform-administration__form");
        form.setWidthFull();
        form.setResponsiveSteps(
                new ResponsiveStep("0", 1),
                new ResponsiveStep("40em", 2),
                new ResponsiveStep("64em", 3));
        form.add(controls);
        return form;
    }

    private static void styleGrid(Grid<?> grid) {
        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        grid.addClassName("platform-administration__grid");
    }

    private static void stylePrimaryAction(Button button) {
        button.addThemeVariants(ButtonVariant.AURA_PRIMARY);
        button.addClassName("platform-administration__form-action");
    }

    private static FlexLayout actionLayout() {
        FlexLayout actions = new FlexLayout();
        actions.setAlignItems(FlexLayout.Alignment.CENTER);
        actions.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        actions.addClassName("platform-administration__actions");
        return actions;
    }

    private void notifyUser(String message) {
        Notification.show(message, 5_000, Position.MIDDLE);
    }
}
