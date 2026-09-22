package br.com.taas.saas.gestaoproducao.platform.administration.application.port.out;

/**
 * Provider-neutral boundary for delivering an invitation link.
 *
 * <p>The port is intentionally optional. The invitation and its copyable link
 * remain the source of truth when no delivery adapter is configured.</p>
 */
public interface InvitationDeliveryPort {

    void deliver(InvitationDeliveryRequest request);
}
