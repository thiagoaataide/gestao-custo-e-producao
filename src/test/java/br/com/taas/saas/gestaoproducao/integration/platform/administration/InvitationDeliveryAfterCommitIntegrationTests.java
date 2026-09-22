package br.com.taas.saas.gestaoproducao.integration.platform.administration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationDeliveryRequested;
import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.InvitationDeliveryPort;
import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.InvitationDeliveryRequest;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventPage;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEventQuery;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "platform.invitation.delivery.enabled=true")
@Import(InvitationDeliveryAfterCommitIntegrationTests.TestSupportConfiguration.class)
class InvitationDeliveryAfterCommitIntegrationTests {

    private static final UUID INVITATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID ACTOR_IDENTITY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final Instant NOW = Instant.parse("2026-09-22T16:00:00Z");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RecordingDeliveryPort deliveryPort;

    @Autowired
    private RecordingAuditRepository auditRepository;

    @AfterEach
    void clearRecords() {
        deliveryPort.requests.clear();
        deliveryPort.fail = false;
        auditRepository.events.clear();
    }

    @Test
    void doesNotDeliverBeforeCommitAndDeliversAfterCommit() {
        InvitationDeliveryRequest request = request();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new InvitationDeliveryRequested(request));
            assertThat(deliveryPort.requests).isEmpty();
        });

        assertThat(deliveryPort.requests).containsExactly(request);
    }

    @Test
    void deliveryFailureDoesNotThrowAfterCommitAndIsAudited() {
        deliveryPort.fail = true;
        InvitationDeliveryRequest request = request();

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new InvitationDeliveryRequested(request)));

        assertThat(deliveryPort.requests).containsExactly(request);
        assertThat(auditRepository.events).singleElement()
                .extracting(AuditEvent::targetId)
                .isEqualTo(INVITATION_ID);
    }

    private static InvitationDeliveryRequest request() {
        return new InvitationDeliveryRequest(
                INVITATION_ID,
                ACTOR_IDENTITY_ID,
                "user@example.com",
                "https://app.example/invitations/opaque-token",
                NOW);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSupportConfiguration {

        @Bean
        RecordingDeliveryPort recordingDeliveryPort() {
            return new RecordingDeliveryPort();
        }

        @Bean
        @Primary
        RecordingAuditRepository recordingAuditRepository() {
            return new RecordingAuditRepository();
        }
    }

    static class RecordingDeliveryPort implements InvitationDeliveryPort {

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

    static class RecordingAuditRepository implements AdministrativeAuditRepository {

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
