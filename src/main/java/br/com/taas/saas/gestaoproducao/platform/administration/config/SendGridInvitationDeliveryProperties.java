package br.com.taas.saas.gestaoproducao.platform.administration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Configuration for the optional outbound invitation-email adapter. */
@ConfigurationProperties(prefix = "platform.invitation.delivery.sendgrid")
public record SendGridInvitationDeliveryProperties(String apiKey, String fromEmail) {

    public boolean configured() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(fromEmail);
    }

    /** Never include the API key or sender address in accidental logs. */
    @Override
    public String toString() {
        return "SendGridInvitationDeliveryProperties[configured=" + configured() + "]";
    }
}
