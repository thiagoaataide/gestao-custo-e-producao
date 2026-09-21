package br.com.taas.saas.gestaoproducao.ui.access;

import java.util.Objects;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecision;
import br.com.taas.saas.gestaoproducao.platform.access.application.AccessDecisionResolver;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

@Component
public final class AccessShellStateResolver {

    private final AccessDecisionResolver accessDecisionResolver;

    public AccessShellStateResolver(AccessDecisionResolver accessDecisionResolver) {
        this.accessDecisionResolver = Objects.requireNonNull(
                accessDecisionResolver,
                "accessDecisionResolver must not be null");
    }

    public AccessShellState resolve(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return AccessShellState.UNAUTHENTICATED;
        }

        if (!(authentication.getPrincipal() instanceof ExternalSubject subject)) {
            return AccessShellState.NOT_PROVISIONED;
        }

        return resolve(accessDecisionResolver.resolve(subject));
    }

    AccessShellState resolve(AccessDecision decision) {
        return switch (decision.type()) {
            case TENANT_ACCESS -> AccessShellState.PROVISIONED;
            case PLATFORM_ACCESS -> AccessShellState.PLATFORM_ACCESS;
            case NOT_PROVISIONED -> AccessShellState.NOT_PROVISIONED;
            case AMBIGUOUS_MEMBERSHIP -> AccessShellState.AMBIGUOUS_MEMBERSHIP;
        };
    }
}
