package br.com.taas.saas.gestaoproducao.integration.platform.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditMetadata;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditTargetType;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;

@SpringBootTest
@ActiveProfiles("test")
class AdministrativeAuditPersistenceIntegrationTests {

    private static final UUID IDENTITY_A =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID IDENTITY_B =
            UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID TENANT_A =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant BASE_TIME = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private AdministrativeAuditRepository auditRepository;

    @Test
    @Transactional
    void persistsCompleteSuccessfulAdministrativeEvent() {
        AuditEvent event = event(
                UUID.randomUUID(),
                IDENTITY_A,
                AuditAction.TENANT_CREATED,
                AuditTargetType.TENANT,
                TENANT_A,
                AuditResult.SUCCESS,
                BASE_TIME,
                AuditMetadata.of(Map.of("new_status", "ACTIVE")));

        auditRepository.save(event);

        AuditEvent persisted = auditRepository.findPage(AuditEventQuery.firstPage(10))
                .content()
                .stream()
                .filter(candidate -> candidate.id().equals(event.id()))
                .findFirst()
                .orElseThrow();

        assertThat(persisted.actorIdentityId()).isEqualTo(IDENTITY_A);
        assertThat(persisted.action()).isEqualTo(AuditAction.TENANT_CREATED);
        assertThat(persisted.targetType()).isEqualTo(AuditTargetType.TENANT);
        assertThat(persisted.targetId()).isEqualTo(TENANT_A);
        assertThat(persisted.result()).isEqualTo(AuditResult.SUCCESS);
        assertThat(persisted.occurredAt()).isEqualTo(BASE_TIME);
        assertThat(persisted.metadata().values()).containsEntry("new_status", "ACTIVE");
    }

    @Test
    @Transactional
    void persistsDeniedEventAndRejectsSensitiveOrOperationalMetadata() {
        AuditEvent denied = event(
                UUID.randomUUID(),
                IDENTITY_B,
                AuditAction.INVITATION_CREATED,
                AuditTargetType.INVITATION,
                UUID.randomUUID(),
                AuditResult.DENIED,
                BASE_TIME.plusSeconds(1),
                AuditMetadata.of(Map.of("reason", "not authorized")));

        auditRepository.save(denied);

        AuditEvent persisted = auditRepository.findPage(new AuditEventQuery(
                        IDENTITY_B,
                        AuditAction.INVITATION_CREATED,
                        AuditTargetType.INVITATION,
                        denied.targetId(),
                        AuditResult.DENIED,
                        null,
                        null,
                        0,
                        10))
                .content()
                .getFirst();

        assertThat(persisted.result()).isEqualTo(AuditResult.DENIED);
        assertThat(persisted.targetType()).isEqualTo(AuditTargetType.INVITATION);
        assertThat(persisted.metadata().values()).containsEntry("reason", "not authorized");
        assertThatThrownBy(() -> AuditMetadata.of(Map.of("token", "raw-token")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditMetadata.of(Map.of("operations_table", "orders")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Transactional
    void filtersAndPaginatesOnlyAdministrativeEvents() {
        auditRepository.save(event(
                UUID.randomUUID(),
                IDENTITY_A,
                AuditAction.TENANT_CREATED,
                AuditTargetType.TENANT,
                TENANT_A,
                AuditResult.SUCCESS,
                BASE_TIME,
                AuditMetadata.of(Map.of("new_status", "ACTIVE"))));
        auditRepository.save(event(
                UUID.randomUUID(),
                IDENTITY_A,
                AuditAction.TENANT_SUSPENDED,
                AuditTargetType.TENANT,
                TENANT_A,
                AuditResult.SUCCESS,
                BASE_TIME.plusSeconds(60),
                AuditMetadata.of(Map.of("new_status", "SUSPENDED"))));
        auditRepository.save(event(
                UUID.randomUUID(),
                IDENTITY_B,
                AuditAction.INVITATION_CREATED,
                AuditTargetType.INVITATION,
                UUID.randomUUID(),
                AuditResult.SUCCESS,
                BASE_TIME.plusSeconds(120),
                AuditMetadata.of(Map.of("email", "invite@example.com"))));

        AuditEventQuery firstQuery = new AuditEventQuery(
                IDENTITY_A,
                null,
                AuditTargetType.TENANT,
                TENANT_A,
                AuditResult.SUCCESS,
                null,
                null,
                0,
                1);
        AuditEventPage firstPage = auditRepository.findPage(firstQuery);
        AuditEventPage secondPage = auditRepository.findPage(new AuditEventQuery(
                IDENTITY_A,
                null,
                AuditTargetType.TENANT,
                TENANT_A,
                AuditResult.SUCCESS,
                null,
                null,
                1,
                1));

        assertThat(firstPage.totalElements()).isEqualTo(2);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.content()).hasSize(1);
        assertThat(firstPage.content().getFirst().action())
                .isEqualTo(AuditAction.TENANT_SUSPENDED);
        assertThat(secondPage.content()).hasSize(1);
        assertThat(secondPage.content().getFirst().action())
                .isEqualTo(AuditAction.TENANT_CREATED);
        assertThat(firstPage.content())
                .allMatch(event -> event.targetType() != AuditTargetType.MEMBERSHIP);
    }

    private AuditEvent event(
            UUID id,
            UUID actorIdentityId,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            AuditResult result,
            Instant occurredAt,
            AuditMetadata metadata) {
        return new AuditEvent(
                id,
                actorIdentityId,
                action,
                targetType,
                targetId,
                result,
                occurredAt,
                metadata);
    }
}
