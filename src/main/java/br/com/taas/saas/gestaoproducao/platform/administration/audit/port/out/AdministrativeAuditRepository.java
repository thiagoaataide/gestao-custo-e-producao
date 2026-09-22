package br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out;

import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;

public interface AdministrativeAuditRepository {

    AuditEvent save(AuditEvent event);

    AuditEventPage findPage(AuditEventQuery query);
}
