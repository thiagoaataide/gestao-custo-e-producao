package br.com.taas.saas.gestaoproducao.operations.application.catalog.establishment;

import java.util.Objects;
import java.util.UUID;

import br.com.taas.saas.gestaoproducao.operations.domain.model.catalog.establishment.Establishment;

public record EstablishmentView(UUID id, String name) {

    public EstablishmentView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }

    public static EstablishmentView from(Establishment establishment) {
        return new EstablishmentView(establishment.id(), establishment.displayName());
    }
}
