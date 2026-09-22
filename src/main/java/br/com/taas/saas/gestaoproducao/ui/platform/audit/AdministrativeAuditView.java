package br.com.taas.saas.gestaoproducao.ui.platform.audit;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationDeniedException;
import br.com.taas.saas.gestaoproducao.platform.access.application.PlatformAuthorizationService;
import br.com.taas.saas.gestaoproducao.platform.administration.application.PlatformActorIdentityResolver;
import br.com.taas.saas.gestaoproducao.platform.administration.application.audit.AdministrativeAuditQueryService;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditTargetType;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

@Route("platform/audit")
@PageTitle("Auditoria administrativa | Gestão de Produção")
public final class AdministrativeAuditView extends VerticalLayout {

    private final AdministrativeAuditQueryService queryService;
    private final PlatformActorIdentityResolver actorIdentityResolver;
    private final PlatformAuthorizationService authorizationService;

    private UUID actorIdentityId;
    private AdministrativeAuditViewState state;
    private Grid<AuditEvent> auditGrid;
    private Span feedback;
    private Span emptyState;
    private Span pageSummary;
    private Button previousPage;
    private Button nextPage;
    private ComboBox<AuditAction> actionFilter;
    private ComboBox<AuditTargetType> targetTypeFilter;
    private ComboBox<AuditResult> resultFilter;
    private TextField actorFilter;
    private TextField targetFilter;
    private DateTimePicker occurredFromFilter;
    private DateTimePicker occurredUntilFilter;

    public AdministrativeAuditView(
            AdministrativeAuditQueryService queryService,
            PlatformActorIdentityResolver actorIdentityResolver,
            PlatformAuthorizationService authorizationService) {
        this.queryService = Objects.requireNonNull(queryService);
        this.actorIdentityResolver = Objects.requireNonNull(actorIdentityResolver);
        this.authorizationService = Objects.requireNonNull(authorizationService);
        this.state = loadState();
        render();
    }

    AdministrativeAuditView(AdministrativeAuditViewState state) {
        this.queryService = null;
        this.actorIdentityResolver = null;
        this.authorizationService = null;
        this.state = Objects.requireNonNull(state);
        render();
    }

    private AdministrativeAuditViewState loadState() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof ExternalSubject subject)) {
                return AdministrativeAuditViewState.denied();
            }
            actorIdentityId = actorIdentityResolver.requireIdentityId(subject);
            authorizationService.requirePlatformAccess(actorIdentityId);
            return AdministrativeAuditViewState.loaded(
                    queryService.findPage(
                            actorIdentityId,
                            AuditEventQuery.firstPage(AdministrativeAuditViewState.PAGE_SIZE)));
        } catch (PlatformAuthorizationDeniedException | IllegalArgumentException exception) {
            return AdministrativeAuditViewState.denied();
        } catch (RuntimeException exception) {
            return AdministrativeAuditViewState.databaseFailure();
        }
    }

    private void render() {
        setWidthFull();
        setMaxWidth("90rem");
        setMargin(true);
        setSpacing(true);

        add(new H1("Auditoria administrativa"));
        if (!state.platformAccess()) {
            add(new Paragraph(state.feedback()));
            return;
        }

        add(new Paragraph(
                "Consulte eventos de administração da plataforma. Dados operacionais não são exibidos."));
        add(filterSection());

        feedback = new Span(state.feedback());
        feedback.setVisible(!state.feedback().isBlank());
        add(feedback);

        auditGrid = new Grid<>();
        auditGrid.setWidthFull();
        auditGrid.addColumn(event -> event.occurredAt().toString()).setHeader("Ocorrido em");
        auditGrid.addColumn(event -> actorLabel(event.actorIdentityId())).setHeader("Ator");
        auditGrid.addColumn(event -> event.action().name()).setHeader("Ação");
        auditGrid.addColumn(event -> event.targetType().name()).setHeader("Alvo");
        auditGrid.addColumn(event -> targetLabel(event.targetId())).setHeader("Identificador do alvo");
        auditGrid.addColumn(event -> event.result().name()).setHeader("Resultado");
        auditGrid.setItems(state.page().content());
        add(auditGrid);

        emptyState = new Span("Nenhum evento administrativo encontrado.");
        emptyState.setVisible(state.page().content().isEmpty() && state.feedback().isBlank());
        add(emptyState);
        add(paginationSection());
    }

    private Component filterSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new Span("Filtros"));

        actionFilter = new ComboBox<>("Ação");
        actionFilter.setItems(AuditAction.values());
        actionFilter.setPlaceholder("Todas");

        targetTypeFilter = new ComboBox<>("Tipo do alvo");
        targetTypeFilter.setItems(AuditTargetType.values());
        targetTypeFilter.setPlaceholder("Todos");

        resultFilter = new ComboBox<>("Resultado");
        resultFilter.setItems(AuditResult.values());
        resultFilter.setPlaceholder("Todos");

        actorFilter = new TextField("Ator (UUID)");
        targetFilter = new TextField("Alvo (UUID)");
        occurredFromFilter = new DateTimePicker("De (UTC)");
        occurredUntilFilter = new DateTimePicker("Até (UTC)");

        Button filter = new Button("Filtrar", event -> applyFilters(0));
        Button clear = new Button("Limpar", event -> clearFilters());
        section.add(
                new HorizontalLayout(actionFilter, targetTypeFilter, resultFilter),
                new HorizontalLayout(actorFilter, targetFilter),
                new HorizontalLayout(occurredFromFilter, occurredUntilFilter, filter, clear));
        return section;
    }

    private Component paginationSection() {
        previousPage = new Button("Anterior", event -> loadPage(state.page().page() - 1));
        nextPage = new Button("Próxima", event -> loadPage(state.page().page() + 1));
        pageSummary = new Span();
        updatePaginationControls();
        return new HorizontalLayout(previousPage, pageSummary, nextPage);
    }

    private void applyFilters(int page) {
        try {
            loadPage(buildQuery(page));
        } catch (IllegalArgumentException exception) {
            showFeedback("Filtro inválido. Informe UUIDs válidos e um período consistente.");
        }
    }

    private void clearFilters() {
        actionFilter.clear();
        targetTypeFilter.clear();
        resultFilter.clear();
        actorFilter.clear();
        targetFilter.clear();
        occurredFromFilter.clear();
        occurredUntilFilter.clear();
        applyFilters(0);
    }

    private AuditEventQuery buildQuery(int page) {
        return new AuditEventQuery(
                parseUuid(actorFilter),
                actionFilter.getValue(),
                targetTypeFilter.getValue(),
                parseUuid(targetFilter),
                resultFilter.getValue(),
                toInstant(occurredFromFilter.getValue()),
                toInstant(occurredUntilFilter.getValue()),
                page,
                AdministrativeAuditViewState.PAGE_SIZE);
    }

    private void loadPage(AuditEventQuery query) {
        if (queryService == null || actorIdentityId == null) {
            showFeedback("Não foi possível consultar a auditoria neste momento.");
            return;
        }
        try {
            AuditEventPage page = queryService.findPage(actorIdentityId, query);
            state = AdministrativeAuditViewState.loaded(page);
            auditGrid.setItems(page.content());
            showFeedback("");
            updatePaginationControls();
        } catch (PlatformAuthorizationDeniedException exception) {
            state = AdministrativeAuditViewState.denied();
            removeAll();
            render();
        } catch (RuntimeException exception) {
            showFeedback("Não foi possível consultar a auditoria neste momento.");
        }
    }

    private void loadPage(int page) {
        try {
            loadPage(buildQuery(page));
        } catch (IllegalArgumentException exception) {
            showFeedback("Filtro inválido. Informe UUIDs válidos e um período consistente.");
        }
    }

    private void showFeedback(String message) {
        state = AdministrativeAuditViewState.loaded(state.page(), message);
        if (feedback != null) {
            feedback.setText(message);
            feedback.setVisible(!message.isBlank());
        }
        if (emptyState != null) {
            emptyState.setVisible(state.page().content().isEmpty() && message.isBlank());
        }
        updatePaginationControls();
    }

    private void updatePaginationControls() {
        if (pageSummary == null || previousPage == null || nextPage == null) {
            return;
        }
        int totalPages = state.page().totalPages();
        pageSummary.setText(totalPages == 0
                ? "Nenhuma página"
                : "Página " + (state.page().page() + 1) + " de " + totalPages
                        + " (" + state.page().totalElements() + " eventos)");
        previousPage.setEnabled(state.page().page() > 0);
        nextPage.setEnabled(totalPages > 0 && state.page().page() + 1 < totalPages);
    }

    private static UUID parseUuid(TextField field) {
        String value = field.getValue();
        return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String actorLabel(UUID identityId) {
        return identityId == null ? "Sistema" : identityId.toString();
    }

    private static String targetLabel(UUID targetId) {
        return targetId == null ? "-" : targetId.toString();
    }
}
