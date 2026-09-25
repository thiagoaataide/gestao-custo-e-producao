package br.com.taas.saas.gestaoproducao.ui.platform;

import java.time.Instant;
import java.util.List;
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
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
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
    private Grid<PlatformRoleAdministrationView> roleGrid;
    private Span tenantMembershipSummary;
    private ComboBox<Tenant> invitationTenant;
    private EmailField invitationEmail;
    private ComboBox<Tenant> memberTenantFilter;
    private ComboBox<MemberStatusFilter> memberStatusFilter;
    private VerticalLayout memberRows;
    private VerticalLayout invitationForm;
    private Button addMemberButton;
    private Dialog pendingRevocationDialog;
    private Dialog lastInvitationLinkDialog;
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

    PlatformAdministrationView(
            PlatformAdministrationViewState state,
            UUID actorIdentityId,
            TenantProvisioningQueryService tenantQueryService,
            InvitationCommandService invitationCommandService,
            InvitationProvisioningQueryService invitationQueryService,
            PlatformMembershipAdminService membershipAdminService) {
        this.tenantCommandService = null;
        this.tenantQueryService = tenantQueryService;
        this.invitationCommandService = invitationCommandService;
        this.invitationQueryService = invitationQueryService;
        this.membershipAdminService = membershipAdminService;
        this.actorIdentityResolver = null;
        this.authorizationService = null;
        this.authenticationContext = null;
        this.actorIdentityId = Objects.requireNonNull(actorIdentityId);
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

    private Component membershipSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.addClassName("platform-administration__panel");
        FlexLayout heading = new FlexLayout();
        heading.add(new H2("Membros"));
        addMemberButton = new Button("Adicionar membro", event -> toggleInvitationForm(true));
        stylePrimaryAction(addMemberButton);
        heading.add(addMemberButton);
        heading.setWidthFull();
        heading.setAlignItems(Alignment.CENTER);
        heading.setJustifyContentMode(FlexLayout.JustifyContentMode.BETWEEN);
        heading.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        section.add(heading);
        section.add(new Paragraph(
                "Acompanhe os acessos ativos e os convites. O acesso começa após a pessoa verificar o e-mail e aceitar o convite."));

        memberTenantFilter = new ComboBox<>("Tenant");
        memberTenantFilter.setItemLabelGenerator(Tenant::name);
        memberTenantFilter.setItems(state.tenants());
        memberTenantFilter.setPlaceholder("Todos os tenants");
        memberTenantFilter.setClearButtonVisible(true);
        memberTenantFilter.addValueChangeListener(event -> renderMemberRows());
        memberStatusFilter = new ComboBox<>("Situação");
        memberStatusFilter.setItemLabelGenerator(MemberStatusFilter::label);
        memberStatusFilter.setItems(MemberStatusFilter.values());
        memberStatusFilter.setValue(MemberStatusFilter.ALL);
        memberStatusFilter.addValueChangeListener(event -> renderMemberRows());
        section.add(responsiveForm(memberTenantFilter, memberStatusFilter));

        invitationForm = new VerticalLayout();
        invitationForm.setPadding(false);
        invitationForm.setSpacing(true);
        invitationForm.addClassName("platform-administration__invite-form");
        invitationForm.setVisible(false);
        invitationForm.add(new H3("Adicionar membro"));
        invitationForm.add(new Paragraph(
                "O convite fica pendente até a pessoa entrar com a conta cujo e-mail verificado corresponde ao destinatário e confirmar o aceite."));
        invitationTenant = new ComboBox<>("Tenant");
        invitationTenant.setItemLabelGenerator(Tenant::name);
        invitationTenant.setItems(state.tenants().stream().filter(Tenant::isAvailable).toList());
        invitationTenant.setRequired(true);
        invitationTenant.setClearButtonVisible(true);
        invitationEmail = new EmailField("E-mail do destinatário");
        invitationEmail.setRequired(true);
        invitationEmail.getElement().setAttribute("autocomplete", "email");
        invitationEmail.getElement().setAttribute("spellcheck", "false");
        Button cancel = new Button("Cancelar", event -> toggleInvitationForm(false));
        Button create = new Button("Criar convite", event -> createInvitation());
        stylePrimaryAction(create);
        HorizontalLayout formActions = new HorizontalLayout(cancel, create);
        formActions.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        invitationForm.add(responsiveForm(invitationTenant, invitationEmail), formActions);
        section.add(invitationForm);

        Span guidance = new Span(
                "O envio de e-mail é opcional nesta V0; o link pode ser copiado para compartilhamento por um canal seguro.");
        guidance.addClassName("platform-administration__hint");
        section.add(guidance);
        memberRows = new VerticalLayout();
        memberRows.setPadding(false);
        memberRows.setSpacing(true);
        memberRows.addClassName("platform-administration__member-list");
        section.add(memberRows);
        renderMemberRows();
        return section;
    }

    private void toggleInvitationForm(boolean visible) {
        invitationForm.setVisible(visible);
        addMemberButton.setVisible(!visible);
        if (visible) {
            invitationTenant.focus();
        }
    }

    private void renderMemberRows() {
        if (memberRows == null) {
            return;
        }
        memberRows.removeAll();
        List<MemberDirectoryRow> rows = memberDirectoryRows().stream()
                .filter(row -> memberTenantFilter.getValue() == null
                        || row.tenantId().equals(memberTenantFilter.getValue().id()))
                .filter(row -> memberStatusFilter.getValue() == null
                        || memberStatusFilter.getValue() == MemberStatusFilter.ALL
                        || memberStatusFilter.getValue().matches(row))
                .toList();
        if (rows.isEmpty()) {
            boolean noData = memberDirectoryRows().isEmpty();
            Span empty = new Span(noData
                    ? "Ainda não há membros ou convites. Adicione um membro para começar."
                    : "Nenhum registro corresponde aos filtros selecionados.");
            empty.addClassName("platform-administration__empty-state");
            memberRows.add(empty);
            if (!noData) {
                Button clear = new Button("Limpar filtros", event -> {
                    memberTenantFilter.clear();
                    memberStatusFilter.setValue(MemberStatusFilter.ALL);
                });
                memberRows.add(clear);
            }
            return;
        }
        rows.forEach(row -> memberRows.add(memberCard(row)));
    }

    private List<MemberDirectoryRow> memberDirectoryRows() {
        List<MemberDirectoryRow> rows = new java.util.ArrayList<>();
        for (MembershipAdministrationView membership : state.membershipAdministration().memberships()) {
            InvitationAdministrationView associated = state.invitations().stream()
                    .filter(invitation -> invitation.tenantId().equals(membership.tenantId()))
                    .filter(invitation -> membership.identityId().equals(invitation.identityId()))
                    .max(java.util.Comparator.comparing(InvitationAdministrationView::createdAt))
                    .orElse(null);
            String person = associated == null || associated.email() == null || associated.email().isBlank()
                    ? membership.identityId().toString()
                    : associated.email();
            rows.add(MemberDirectoryRow.membership(membership, person));
        }
        for (InvitationAdministrationView invitation : state.invitations()) {
            if (invitation.status() == InvitationStatus.ACCEPTED) {
                continue;
            }
            rows.add(MemberDirectoryRow.invitation(invitation));
        }
        rows.sort(java.util.Comparator
                .comparing((MemberDirectoryRow row) -> tenantName(row.tenantId()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(MemberDirectoryRow::person, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private Component memberCard(MemberDirectoryRow row) {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(false);
        card.addClassName("platform-administration__member-card");
        H3 person = new H3(row.person());
        person.addClassName("platform-administration__member-person");
        Span details = new Span(tenantName(row.tenantId()) + " · " + row.statusLabel()
                + " · " + row.roleLabel() + " · " + row.dateLabel());
        details.addClassName("platform-administration__member-details");
        card.add(person, details);
        Component actions = row.invitation() != null
                ? invitationActions(row.invitation())
                : membershipActions(row.membership());
        card.add(actions);
        return card;
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
            Button revoke = new Button("Revogar convite");
            revoke.addThemeVariants(ButtonVariant.AURA_DANGER);
            revoke.addClickListener(event -> requestInvitationRevocation(invitation));
            actions.add(revoke);
        }
        if (actions.getComponentCount() == 0) {
            actions.add(new Span("Sem ações"));
        }
        return actions;
    }

    private Component membershipActions(MembershipAdministrationView membership) {
        if (membership.status() != MembershipStatus.ACTIVE
                || membership.role() != MembershipRole.TENANT_USER) {
            return new Span("Sem ações");
        }
        Button revoke = new Button("Revogar acesso");
        revoke.addThemeVariants(ButtonVariant.AURA_DANGER);
        revoke.addClickListener(event -> requestMembershipRevocation(membership));
        return revoke;
    }

    private void requestInvitationRevocation(InvitationAdministrationView invitation) {
        requestRevocation(
                "Revogar convite?",
                invitation.email() + " deixará de poder aceitar o convite do tenant "
                        + tenantName(invitation.tenantId()) + ".",
                "Revogar convite",
                () -> revokeInvitation(invitation));
    }

    private void requestMembershipRevocation(MembershipAdministrationView membership) {
        InvitationAdministrationView associated = state.invitations().stream()
                .filter(invitation -> invitation.tenantId().equals(membership.tenantId()))
                .filter(invitation -> membership.identityId().equals(invitation.identityId()))
                .max(java.util.Comparator.comparing(InvitationAdministrationView::createdAt))
                .orElse(null);
        String person = associated == null || associated.email() == null || associated.email().isBlank()
                ? membership.identityId().toString()
                : associated.email();
        requestRevocation(
                "Revogar acesso?",
                person + " perderá o acesso ao tenant " + tenantName(membership.tenantId()) + ".",
                "Revogar acesso",
                () -> revokeMembership(membership));
    }

    private void requestRevocation(String title, String message, String confirmText, Runnable revoke) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.add(new Paragraph(message));
        Button cancel = new Button("Cancelar", event -> dialog.close());
        Button confirm = new Button(confirmText, event -> {
            dialog.close();
            revoke.run();
        });
        confirm.addThemeVariants(ButtonVariant.AURA_DANGER);
        dialog.getFooter().add(cancel, confirm);
        pendingRevocationDialog = dialog;
        dialog.open();
    }

    Dialog pendingRevocationDialog() {
        return pendingRevocationDialog;
    }

    Dialog lastInvitationLinkDialog() {
        return lastInvitationLinkDialog;
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
            invitationEmail.clear();
            invitationTenant.clear();
            toggleInvitationForm(false);
            refresh();
            showInvitationLink(result, "Convite criado");
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
        lastInvitationLinkDialog = dialog;
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
        memberTenantFilter.setItems(state.tenants());
        renderMemberRows();
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

    private static String formatDate(Instant instant) {
        return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", java.util.Locale.forLanguageTag("pt-BR"))
                .withZone(java.time.ZoneId.systemDefault())
                .format(instant);
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

    private enum MemberStatusFilter {
        ALL("Todas as situações"),
        ACTIVE("Ativos"),
        PENDING("Convites pendentes"),
        EXPIRED("Convites expirados"),
        REVOKED("Convites revogados"),
        ACCESS_REVOKED("Acessos revogados");

        private final String label;

        MemberStatusFilter(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        boolean matches(MemberDirectoryRow row) {
            return switch (this) {
                case ALL -> true;
                case ACTIVE -> row.membership() != null && row.membership().status() == MembershipStatus.ACTIVE;
                case PENDING -> row.invitation() != null && row.invitation().status() == InvitationStatus.PENDING;
                case EXPIRED -> row.invitation() != null && row.invitation().status() == InvitationStatus.EXPIRED;
                case REVOKED -> row.invitation() != null && row.invitation().status() == InvitationStatus.REVOKED;
                case ACCESS_REVOKED -> row.membership() != null && row.membership().status() == MembershipStatus.REVOKED;
            };
        }
    }

    private record MemberDirectoryRow(
            UUID tenantId,
            String person,
            String statusLabel,
            String roleLabel,
            String dateLabel,
            InvitationAdministrationView invitation,
            MembershipAdministrationView membership) {

        static MemberDirectoryRow invitation(InvitationAdministrationView invitation) {
            String status = switch (invitation.status()) {
                case PENDING -> "Convite pendente";
                case EXPIRED -> "Convite expirado";
                case REVOKED -> "Convite revogado";
                case ACCEPTED -> "Convite aceito";
            };
            return new MemberDirectoryRow(
                    invitation.tenantId(),
                    invitation.email(),
                    status,
                    invitation.role().value(),
                    formatDate(invitation.expiresAt()),
                    invitation,
                    null);
        }

        static MemberDirectoryRow membership(MembershipAdministrationView membership, String person) {
            String status = membership.status() == MembershipStatus.ACTIVE
                    ? "Acesso ativo"
                    : membership.status() == MembershipStatus.REVOKED ? "Acesso revogado" : "Acesso pendente";
            return new MemberDirectoryRow(
                    membership.tenantId(),
                    person,
                    status,
                    membership.role().value(),
                    formatDate(membership.createdAt()),
                    null,
                    membership);
        }
    }

}
