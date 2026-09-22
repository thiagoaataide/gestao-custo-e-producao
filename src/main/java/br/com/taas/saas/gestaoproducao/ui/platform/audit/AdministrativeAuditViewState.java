package br.com.taas.saas.gestaoproducao.ui.platform.audit;

import java.util.List;
import java.util.Objects;

import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;

public record AdministrativeAuditViewState(
        boolean platformAccess,
        AuditEventPage page,
        String feedback) {

    public static final int PAGE_SIZE = 20;

    public AdministrativeAuditViewState {
        page = Objects.requireNonNull(page, "page must not be null");
        feedback = feedback == null ? "" : feedback;
    }

    public static AdministrativeAuditViewState denied() {
        return new AdministrativeAuditViewState(
                false,
                emptyPage(),
                "Acesso não autorizado à auditoria administrativa.");
    }

    public static AdministrativeAuditViewState databaseFailure() {
        return new AdministrativeAuditViewState(
                true,
                emptyPage(),
                "Não foi possível consultar a auditoria neste momento.");
    }

    public static AdministrativeAuditViewState loaded(AuditEventPage page) {
        return new AdministrativeAuditViewState(true, page, "");
    }

    public static AdministrativeAuditViewState loaded(
            AuditEventPage page,
            String feedback) {
        return new AdministrativeAuditViewState(true, page, feedback);
    }

    private static AuditEventPage emptyPage() {
        return new AuditEventPage(List.of(), 0, PAGE_SIZE, 0);
    }
}
