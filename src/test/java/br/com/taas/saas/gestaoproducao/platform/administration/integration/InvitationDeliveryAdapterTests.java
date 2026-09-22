package br.com.taas.saas.gestaoproducao.platform.administration.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationDeliveryRequested;
import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.InvitationDeliveryPort;
import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.InvitationDeliveryRequest;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.administration.config.InvitationDeliveryProperties;

class InvitationDeliveryAdapterTests {

    private static final UUID INVITATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ACTOR_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final Instant NOW = Instant.parse("2026-09-22T15:00:00Z");

    @Test
    void deliversRequestWhenEnabledAndAdapterIsPresent() {
        var adapter = new RecordingDeliveryPort();
        var audits = new RecordingAuditRepository();
        var listener = listener(adapter, audits, true);
        InvitationDeliveryRequest request = request();

        listener.deliverAfterCommit(new InvitationDeliveryRequested(request));

        assertThat(adapter.requests).containsExactly(request);
        assertThat(audits.events).isEmpty();
    }

    @Test
    void disabledOrAbsentAdapterDoesNotBlockManualLinkFlow() {
        var adapter = new RecordingDeliveryPort();
        var audits = new RecordingAuditRepository();
        InvitationDeliveryRequested event = new InvitationDeliveryRequested(request());

        listener(adapter, audits, false).deliverAfterCommit(event);
        listener(null, audits, true).deliverAfterCommit(event);

        assertThat(adapter.requests).isEmpty();
        assertThat(audits.events).isEmpty();
    }

    @Test
    void deliveryFailurePreservesRequestAndRecordsSafeAuditMetadata() {
        var adapter = new RecordingDeliveryPort();
        adapter.fail = true;
        var audits = new RecordingAuditRepository();
        InvitationDeliveryRequest request = request();

        listener(adapter, audits, true)
                .deliverAfterCommit(new InvitationDeliveryRequested(request));

        assertThat(adapter.requests).containsExactly(request);
        assertThat(audits.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.INVITATION_CREATED);
            assertThat(event.targetId()).isEqualTo(INVITATION_ID);
            assertThat(event.result().name()).isEqualTo("FAILED");
            assertThat(event.metadata().values())
                    .containsEntry("channel", "EMAIL")
                    .containsEntry("delivery", "FAILED")
                    .doesNotContainKey("link")
                    .doesNotContainKey("recipientEmail");
        });
        assertThat(request.toString()).doesNotContain("token");
        assertThat(request.toString()).doesNotContain("user@example.com");
    }

    private static InvitationDeliveryAfterCommitListener listener(
            InvitationDeliveryPort adapter,
            RecordingAuditRepository audits,
            boolean enabled) {
        return new InvitationDeliveryAfterCommitListener(
                Optional.ofNullable(adapter),
                audits,
                new InvitationDeliveryProperties(enabled));
    }

    private static InvitationDeliveryRequest request() {
        return new InvitationDeliveryRequest(
                INVITATION_ID,
                ACTOR_IDENTITY_ID,
                "user@example.com",
                "https://app.example/invitations/opaque-token",
                NOW);
    }

    private static final class RecordingDeliveryPort implements InvitationDeliveryPort {

        private final List<InvitationDeliveryRequest> requests = new ArrayList<>();
        private boolean fail;

        @Override
        public void deliver(InvitationDeliveryRequest request) {
            requests.add(request);
            if (fail) {
                throw new IllegalStateException("simulated delivery failure");
            }
        }
    }

    private static final class RecordingAuditRepository
            implements AdministrativeAuditRepository {

        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public AuditEvent save(AuditEvent event) {
            events.add(event);
            return event;
        }

        @Override
        public AuditEventPage findPage(AuditEventQuery query) {
            return new AuditEventPage(events, query.page(), query.size(), events.size());
        }
    }
}
