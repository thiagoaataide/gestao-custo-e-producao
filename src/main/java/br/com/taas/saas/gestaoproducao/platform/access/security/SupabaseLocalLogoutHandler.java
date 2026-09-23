package br.com.taas.saas.gestaoproducao.platform.access.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Revokes only the Supabase session represented by the current application login. */
@Component
public final class SupabaseLocalLogoutHandler implements LogoutHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupabaseLocalLogoutHandler.class);

    private final SupabaseAuthClient authClient;

    public SupabaseLocalLogoutHandler(SupabaseAuthClient authClient) {
        this.authClient = authClient;
    }

    @Override
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        if (!(authentication instanceof SupabaseAuthenticationToken supabaseAuthentication)) {
            return;
        }
        try {
            authClient.signOutLocal(supabaseAuthentication.getJwt().getTokenValue());
        } catch (SupabaseAuthClient.ProviderUnavailableException exception) {
            // Spring still clears the local session; never log access or refresh tokens.
            LOGGER.warn("Supabase logout could not be confirmed; the local session was cleared.");
        }
    }
}
