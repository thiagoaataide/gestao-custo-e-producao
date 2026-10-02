package br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.establishment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EstablishmentTests {

    @Test
    void registrationTrimsDisplayNameAndBuildsNormalizedKey() {
        Establishment establishment = Establishment.register(
                UUID.randomUUID(), UUID.randomUUID(), "  Mercado Central  ", Instant.now());

        assertThat(establishment.displayName()).isEqualTo("Mercado Central");
        assertThat(establishment.normalizedName()).isEqualTo("mercado central");
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> Establishment.register(
                UUID.randomUUID(), UUID.randomUUID(), "   ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
