package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

@FunctionalInterface
public interface InvitationTokenGenerator {

    String generate();
}
