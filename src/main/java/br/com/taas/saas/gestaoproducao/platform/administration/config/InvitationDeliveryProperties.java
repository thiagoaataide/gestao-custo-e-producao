package br.com.taas.saas.gestaoproducao.platform.administration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.invitation.delivery")
public record InvitationDeliveryProperties(boolean enabled) {
}
