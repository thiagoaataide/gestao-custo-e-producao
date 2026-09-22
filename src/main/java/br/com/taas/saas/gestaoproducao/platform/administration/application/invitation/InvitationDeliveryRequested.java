package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.util.Objects;

import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.InvitationDeliveryRequest;

public record InvitationDeliveryRequested(InvitationDeliveryRequest request) {

    public InvitationDeliveryRequested {
        Objects.requireNonNull(request, "request must not be null");
    }
}
