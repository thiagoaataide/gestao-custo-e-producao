package br.com.taas.saas.gestaoproducao.integration.operations.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import br.com.taas.saas.gestaoproducao.operations.application.catalog.establishment.EstablishmentCatalogService;
import br.com.taas.saas.gestaoproducao.operations.application.catalog.establishment.EstablishmentNameAlreadyRegisteredException;
import br.com.taas.saas.gestaoproducao.operations.application.catalog.establishment.RegisterEstablishmentCommand;
import br.com.taas.saas.gestaoproducao.platform.access.application.TenantAccessDeniedException;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

@SpringBootTest
@ActiveProfiles("test")
class EstablishmentCatalogIntegrationTests {

    private static final ExternalSubject SUBJECT_A = ExternalSubject.fromSupabase("test-subject-a");
    private static final ExternalSubject BLOCKED_SUBJECT = ExternalSubject.fromSupabase("blocked-subject");

    @Autowired
    private EstablishmentCatalogService establishments;

    @Test
    void registersEstablishmentAndListsItInsideAuthenticatedTenant() {
        String name = "Mercado " + UUID.randomUUID();
        var registered = establishments.register(SUBJECT_A, new RegisterEstablishmentCommand(name));

        assertThat(establishments.findAll(SUBJECT_A))
                .anySatisfy(item -> {
                    assertThat(item.id()).isEqualTo(registered.id());
                    assertThat(item.name()).isEqualTo(name);
                });
    }

    @Test
    void normalizedDuplicateIsRejectedWithinTenant() {
        String name = "Mercado Central " + UUID.randomUUID();
        establishments.register(SUBJECT_A, new RegisterEstablishmentCommand(name));

        assertThatThrownBy(() -> establishments.register(
                SUBJECT_A, new RegisterEstablishmentCommand(name.toUpperCase())))
                .isInstanceOf(EstablishmentNameAlreadyRegisteredException.class);
    }

    @Test
    void unprovisionedSubjectCannotRegisterOrQueryEstablishments() {
        assertThatThrownBy(() -> establishments.register(BLOCKED_SUBJECT,
                new RegisterEstablishmentCommand("Mercado " + UUID.randomUUID())))
                .isInstanceOf(TenantAccessDeniedException.class);
        assertThatThrownBy(() -> establishments.findAll(BLOCKED_SUBJECT))
                .isInstanceOf(TenantAccessDeniedException.class);
    }
}
