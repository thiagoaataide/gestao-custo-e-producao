package br.com.taas.saas.gestaoproducao.operations.application.catalog.establishment;

public class EstablishmentNameAlreadyRegisteredException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public EstablishmentNameAlreadyRegisteredException(String name) {
        super("An establishment with normalized name '" + name + "' is already registered in this tenant");
    }
}
