package br.com.taas.saas.gestaoproducao.platform.administration.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InvitationDeliveryRequest(
        UUID invitationId,
        UUID actorIdentityId,
        String recipientEmail,
        String link,
        Instant occurredAt) {

    public InvitationDeliveryRequest {
        Objects.requireNonNull(invitationId, "invitationId must not be null");
        Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
        Objects.requireNonNull(recipientEmail, "recipientEmail must not be null");
        Objects.requireNonNull(link, "link must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (recipientEmail.isBlank()) {
            throw new IllegalArgumentException("recipientEmail must not be blank");
        }
        if (link.isBlank()) {
            throw new IllegalArgumentException("link must not be blank");
        }
    }

    /**
     * Do not expose the recipient or opaque token-bearing link in accidental
     * logs. The delivery adapter still receives both through accessors.
     */
    @Override
    public String toString() {
        return "InvitationDeliveryRequest[invitationId=" + invitationId
                + ", actorIdentityId=" + actorIdentityId
                + ", occurredAt=" + occurredAt + "]";
    }
}
