package br.com.taas.saas.gestaoproducao.tenancy.application;

@FunctionalInterface
public interface TenantUseCase<R> {

    R execute();
}
