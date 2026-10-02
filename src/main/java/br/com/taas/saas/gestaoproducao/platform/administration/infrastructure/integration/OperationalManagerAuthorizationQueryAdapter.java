package br.com.taas.saas.gestaoproducao.platform.administration.infrastructure.integration;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment.OperationalManagerAuthorizationQuery;

@Repository
public class OperationalManagerAuthorizationQueryAdapter
        implements OperationalManagerAuthorizationQuery {

    private final JdbcTemplate jdbcTemplate;

    public OperationalManagerAuthorizationQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isActiveOperationalManager(UUID identityId, UUID tenantId) {
        Boolean authorized = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                      FROM platform.membership AS membership
                      JOIN platform.operational_manager_assignment AS assignment
                        ON assignment.membership_id = membership.id
                       AND assignment.tenant_id = membership.tenant_id
                     WHERE membership.identity_id = ?
                       AND membership.tenant_id = ?
                       AND membership.status = 'ACTIVE'
                       AND assignment.status = 'ACTIVE'
                )
                """, Boolean.class, identityId, tenantId);
        return Boolean.TRUE.equals(authorized);
    }
}
