package br.com.taas.saas.gestaoproducao.platform.administration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.InvitationDeliveryRequest;
import br.com.taas.saas.gestaoproducao.platform.administration.config.SendGridInvitationDeliveryProperties;

class SendGridInvitationDeliveryAdapterTests {

    private static final String API_KEY = "SG.test-secret-key";
    private static final String RECIPIENT = "invitee@example.com";
    private static final String LINK = "https://app.example/invitations/opaque-test-token";
    private static final String SEND_URL = "https://api.sendgrid.com/v3/mail/send";

    @Test
    void sendsInvitationUsingMailSendV3Contract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.personalizations[0].to[0].email").value(RECIPIENT))
                .andExpect(jsonPath("$.from.email").value("sender@example.com"))
                .andExpect(jsonPath("$.subject").isNotEmpty())
                .andExpect(jsonPath("$.content[0].value").value(org.hamcrest.Matchers.containsString(LINK)))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        adapter(builder.build()).deliver(request());

        server.verify();
    }

    @Test
    void rejectsProviderErrorWithoutLeakingSensitiveData() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("provider echoed " + API_KEY + " " + RECIPIENT + " " + LINK));

        Throwable failure = catchThrowable(() -> adapter(builder.build()).deliver(request()));

        assertThat(failure)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invitation email delivery failed")
                .hasNoCause();
        assertThat(failure.toString()).doesNotContain(API_KEY, RECIPIENT, LINK, "provider echoed");
        server.verify();
    }

    @Test
    void handlesProviderUnavailableWithoutLeakingSensitiveData() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SEND_URL))
                .andRespond(withException(new IOException(
                        "connection failed for " + API_KEY + " " + RECIPIENT + " " + LINK)));

        Throwable failure = catchThrowable(() -> adapter(builder.build()).deliver(request()));

        assertThat(failure)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invitation email delivery failed")
                .hasNoCause();
        assertThat(failure.toString()).doesNotContain(API_KEY, RECIPIENT, LINK, "connection failed");
        server.verify();
    }

    private static SendGridInvitationDeliveryAdapter adapter(RestClient restClient) {
        return new SendGridInvitationDeliveryAdapter(
                restClient,
                new SendGridInvitationDeliveryProperties(API_KEY, "sender@example.com"));
    }

    private static InvitationDeliveryRequest request() {
        return new InvitationDeliveryRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000401"),
                UUID.fromString("00000000-0000-0000-0000-000000000402"),
                RECIPIENT,
                LINK,
                Instant.parse("2026-09-24T12:00:00Z"));
    }
}
