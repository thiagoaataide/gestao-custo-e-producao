package br.com.taas.saas.gestaoproducao.platform.administration.integration;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.InvitationDeliveryPort;
import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.InvitationDeliveryRequest;
import br.com.taas.saas.gestaoproducao.platform.administration.config.SendGridInvitationDeliveryProperties;

/** SendGrid Mail Send v3 adapter; vendor details stay inside the integration module. */
public final class SendGridInvitationDeliveryAdapter implements InvitationDeliveryPort {

    private static final String MAIL_SEND_URL = "https://api.sendgrid.com/v3/mail/send";
    private static final String SUBJECT = "Convite para acessar a plataforma Gestão de Produção";
    private static final String FAILURE_MESSAGE = "Invitation email delivery failed";

    private final RestClient restClient;
    private final String apiKey;
    private final String fromEmail;

    public SendGridInvitationDeliveryAdapter(
            RestClient restClient,
            SendGridInvitationDeliveryProperties properties) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        if (!properties.configured()) {
            throw new IllegalArgumentException("SendGrid delivery configuration is incomplete");
        }
        this.apiKey = properties.apiKey().strip();
        this.fromEmail = properties.fromEmail().strip();
    }

    @Override
    public void deliver(InvitationDeliveryRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        try {
            HttpStatusCode responseStatus = restClient.post()
                    .uri(MAIL_SEND_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(mailPayload(request))
                    .exchange((httpRequest, httpResponse) -> httpResponse.getStatusCode());

            if (responseStatus.value() != 202) {
                throw new InvitationDeliveryException();
            }
        } catch (RestClientException providerFailure) {
            // Do not propagate the response body, URL, recipient, token, or
            // authorization header through exception messages or causes.
            throw new InvitationDeliveryException();
        }
    }

    private Map<String, Object> mailPayload(InvitationDeliveryRequest request) {
        if (!StringUtils.hasText(request.recipientEmail()) || !StringUtils.hasText(request.link())) {
            throw new IllegalArgumentException("Invitation delivery data is incomplete");
        }

        String message = "Você recebeu um convite para acessar a plataforma Gestão de Produção.\n\n"
                + "Para revisar e aceitar o convite, acesse: " + request.link();
        return Map.of(
                "personalizations", List.of(Map.of(
                        "to", List.of(Map.of("email", request.recipientEmail())))),
                "from", Map.of("email", fromEmail),
                "subject", SUBJECT,
                "content", List.of(Map.of("type", "text/plain", "value", message)));
    }

    private static final class InvitationDeliveryException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private InvitationDeliveryException() {
            super(FAILURE_MESSAGE);
        }
    }
}
