package br.com.taas.saas.gestaoproducao.operations.application.catalog.establishment;

import java.util.Objects;

public record RegisterEstablishmentCommand(String name) {

    public RegisterEstablishmentCommand {
        Objects.requireNonNull(name, "name must not be null");
    }
}
