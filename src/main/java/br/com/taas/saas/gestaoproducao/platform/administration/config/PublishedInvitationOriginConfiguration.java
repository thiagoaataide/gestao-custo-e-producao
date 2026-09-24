package br.com.taas.saas.gestaoproducao.platform.administration.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!local & !test")
public class PublishedInvitationOriginConfiguration {

    public PublishedInvitationOriginConfiguration(InvitationLinkProperties properties) {
        properties.requirePublicOrigin();
    }
}
