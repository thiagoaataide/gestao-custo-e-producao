package br.com.taas.saas.gestaoproducao.platform.administration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class InvitationLinkPropertiesTests {

    @Test
    void localHttpOriginIsAcceptedAndTrailingSlashIsNormalized() {
        InvitationLinkProperties properties = new InvitationLinkProperties(" http://localhost:8080/// ");

        assertThat(properties.linkFor("opaque-token"))
                .isEqualTo("http://localhost:8080/invitations/opaque-token");
        assertThatThrownBy(properties::requirePublicOrigin)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicHttpsOriginIsAccepted() {
        InvitationLinkProperties properties = new InvitationLinkProperties(
                "https://gestao-custo-e-producao.onrender.com/");

        properties.requirePublicOrigin();
        assertThat(properties.linkFor("opaque-token"))
                .isEqualTo("https://gestao-custo-e-producao.onrender.com/invitations/opaque-token");
    }

    @Test
    void publishedOriginRejectsInsecureLocalAndPrivateOrigins() {
        List.of(
                "http://gestao-custo-e-producao.onrender.com",
                "https://localhost:8080",
                "https://app.localhost",
                "https://127.0.0.1",
                "https://127.1",
                "https://10.0.0.8",
                "https://[::1]",
                "https://[fd00::1]",
                "https://intranet")
                .forEach(origin -> assertThatThrownBy(
                        () -> new InvitationLinkProperties(origin).requirePublicOrigin())
                                .as("origin %s must not be accepted", origin)
                                .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void baseUrlMustBeAnOriginWithoutPathQueryOrUserInfo() {
        List.of(
                "https://app.example/invitations",
                "https://app.example?target=elsewhere",
                "https://user@app.example")
                .forEach(origin -> assertThatThrownBy(() -> new InvitationLinkProperties(origin))
                        .as("origin %s must not be accepted", origin)
                        .isInstanceOf(IllegalArgumentException.class));
    }
}
