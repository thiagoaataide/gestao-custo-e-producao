package br.com.taas.saas.gestaoproducao.platform.administration.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    PlatformBootstrapProperties.class,
    InvitationLinkProperties.class,
    InvitationDeliveryProperties.class,
    SendGridInvitationDeliveryProperties.class
})
public class PlatformAdministrationConfiguration {
}
