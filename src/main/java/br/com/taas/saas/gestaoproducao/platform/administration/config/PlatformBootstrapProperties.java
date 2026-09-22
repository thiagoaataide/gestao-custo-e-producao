package br.com.taas.saas.gestaoproducao.platform.administration.config;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

@ConfigurationProperties(prefix = "platform.bootstrap")
public record PlatformBootstrapProperties(String ownerSubject) {

    public PlatformBootstrapProperties {
        ownerSubject = ownerSubject == null ? "" : ownerSubject.trim();
    }

    public boolean authorizes(ExternalSubject subject) {
        Objects.requireNonNull(subject, "subject must not be null");
        return !ownerSubject.isBlank()
                && ExternalSubject.SUPABASE_PROVIDER.equals(subject.provider())
                && ownerSubject.equals(subject.value());
    }
}
