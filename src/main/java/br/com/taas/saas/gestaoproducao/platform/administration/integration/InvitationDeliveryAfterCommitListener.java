package br.com.taas.saas.gestaoproducao.platform.administration.integration;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationDeliveryRequested;
import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.InvitationDeliveryPort;
import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.InvitationDeliveryRequest;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditMetadata;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditTargetType;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.administration.config.InvitationDeliveryProperties;

@Component
public class InvitationDeliveryAfterCommitListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            InvitationDeliveryAfterCommitListener.class);

    private final Optional<InvitationDeliveryPort> deliveryPort;
    private final AdministrativeAuditRepository auditRepository;
    private final InvitationDeliveryProperties properties;

    public InvitationDeliveryAfterCommitListener(
            Optional<InvitationDeliveryPort> deliveryPort,
            AdministrativeAuditRepository auditRepository,
            InvitationDeliveryProperties properties) {
        this.deliveryPort = Objects.requireNonNull(deliveryPort, "deliveryPort must not be null");
        this.auditRepository = Objects.requireNonNull(
                auditRepository,
                "auditRepository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliverAfterCommit(InvitationDeliveryRequested event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!properties.enabled() || deliveryPort.isEmpty()) {
            return;
        }

        try {
            deliveryPort.get().deliver(event.request());
        } catch (RuntimeException deliveryFailure) {
            recordDeliveryFailure(event.request(), deliveryFailure);
        }
    }

    private void recordDeliveryFailure(
            InvitationDeliveryRequest request,
            RuntimeException deliveryFailure) {
        try {
            auditRepository.save(new AuditEvent(
                    UUID.randomUUID(),
                    request.actorIdentityId(),
                    AuditAction.INVITATION_CREATED,
                    AuditTargetType.INVITATION,
                    request.invitationId(),
                    AuditResult.FAILED,
                    request.occurredAt(),
                    AuditMetadata.of(Map.of(
                            "channel", "EMAIL",
                            "delivery", "FAILED",
                            "error_type", deliveryFailure.getClass().getSimpleName()))));
        } catch (RuntimeException auditFailure) {
            // The invitation is already committed. Keep the callback from
            // leaking provider or persistence messages, which may contain PII.
            LOGGER.warn(
                    "Could not record invitation delivery failure for {} ({})",
                    request.invitationId(),
                    auditFailure.getClass().getSimpleName());
        }
    }
}
