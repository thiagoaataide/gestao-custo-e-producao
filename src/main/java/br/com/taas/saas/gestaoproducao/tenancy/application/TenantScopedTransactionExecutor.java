package br.com.taas.saas.gestaoproducao.tenancy.application;

import java.sql.Connection;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecision;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionResolver;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionType;
import br.com.taas.saas.gestaoproducao.platform.access.application.TenantAccessDeniedException;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.tenancy.application.port.out.TenantContextWriter;
import br.com.taas.saas.gestaoproducao.tenancy.model.TenantAccessContext;

/**
 * Executes tenant-scoped application work only after access resolution and RLS
 * context propagation have succeeded in the same local transaction.
 */
@Service
public class TenantScopedTransactionExecutor {

    private final AccessDecisionResolver accessDecisionResolver;
    private final TenantContextWriter tenantContextWriter;
    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;

    public TenantScopedTransactionExecutor(
            AccessDecisionResolver accessDecisionResolver,
            TenantContextWriter tenantContextWriter,
            DataSource dataSource,
            PlatformTransactionManager transactionManager) {
        this.accessDecisionResolver = Objects.requireNonNull(
                accessDecisionResolver,
                "accessDecisionResolver must not be null");
        this.tenantContextWriter = Objects.requireNonNull(
                tenantContextWriter,
                "tenantContextWriter must not be null");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(
                transactionManager,
                "transactionManager must not be null"));
    }

    public <R> R execute(ExternalSubject subject, TenantUseCase<R> useCase) {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(useCase, "useCase must not be null");

        AccessDecision decision = accessDecisionResolver.resolve(subject);
        if (decision.type() != AccessDecisionType.TENANT_ACCESS) {
            throw new TenantAccessDeniedException(decision.type());
        }

        return execute(decision.tenantContext(), useCase);
    }

    public <R> R execute(TenantAccessContext context, TenantUseCase<R> useCase) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(useCase, "useCase must not be null");

        return transactionTemplate.execute(status -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            tenantContextWriter.apply(context.tenantId(), connection);
            return useCase.execute();
        });
    }
}
