package br.com.taas.saas.gestaoproducao.platform.administration.config;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.invitation")
public record InvitationLinkProperties(String baseUrl) {

    public InvitationLinkProperties {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        baseUrl = baseUrl.trim();
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }

    public String linkFor(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");
        if (rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }
        return baseUrl + "/invitations/" + rawToken;
    }
}
