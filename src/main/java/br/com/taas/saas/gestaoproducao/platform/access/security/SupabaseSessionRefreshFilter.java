package br.com.taas.saas.gestaoproducao.platform.access.security;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;

/** Refreshes an imminent-to-expire Supabase session while keeping tokens server-side. */
public final class SupabaseSessionRefreshFilter extends OncePerRequestFilter {

    private static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofSeconds(60);

    private final SupabaseAuthClient authClient;
    private final JwtDecoder jwtDecoder;
    private final SupabaseJwtAuthenticationConverter authenticationConverter;
    private final SecurityContextRepository securityContextRepository;

    public SupabaseSessionRefreshFilter(
            SupabaseAuthClient authClient,
            JwtDecoder jwtDecoder,
            SupabaseJwtAuthenticationConverter authenticationConverter,
            SecurityContextRepository securityContextRepository) {
        this.authClient = authClient;
        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication requestAuthentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(requestAuthentication instanceof SupabaseAuthenticationToken authentication)
                || !authentication.hasRefreshToken()) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean sessionInvalidated;
        synchronized (session) {
            sessionInvalidated = refreshIfNeeded(request, response, session, authentication);
        }
        if (sessionInvalidated) {
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    private boolean refreshIfNeeded(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session,
            SupabaseAuthenticationToken requestAuthentication) {
        Object savedContext = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        SecurityContext context = savedContext instanceof SecurityContext saved
                ? saved
                : SecurityContextHolder.getContext();
        Authentication savedAuthentication = context.getAuthentication();
        SupabaseAuthenticationToken authentication = savedAuthentication instanceof SupabaseAuthenticationToken saved
                ? saved
                : requestAuthentication;

        if (!authentication.hasRefreshToken()) {
            return false;
        }
        if (!shouldRefresh(authentication.getJwt())) {
            if (context != SecurityContextHolder.getContext()) {
                SecurityContextHolder.setContext(context);
            }
            return false;
        }

        try {
            SupabaseAuthClient.SupabaseAuthSession rotated = authClient.refresh(authentication.refreshToken());
            Jwt jwt = jwtDecoder.decode(rotated.accessToken());
            SupabaseAuthenticationToken validated = authenticationConverter.convert(jwt);
            ExternalSubject existingSubject = (ExternalSubject) authentication.getPrincipal();
            ExternalSubject validatedSubject = (ExternalSubject) validated.getPrincipal();
            if (!existingSubject.equals(validatedSubject)
                    || !existingSubject.equals(ExternalSubject.fromSupabase(rotated.subject()))) {
                throw new SupabaseAuthClient.InvalidAuthResponseException();
            }

            SecurityContext refreshedContext = SecurityContextHolder.createEmptyContext();
            refreshedContext.setAuthentication(new SupabaseAuthenticationToken(
                    jwt,
                    existingSubject,
                    rotated.refreshToken()));
            SecurityContextHolder.setContext(refreshedContext);
            securityContextRepository.saveContext(refreshedContext, request, response);
            return false;
        } catch (SupabaseAuthClient.ProviderUnavailableException exception) {
            if (!isExpired(authentication.getJwt())) {
                SecurityContextHolder.setContext(context);
                return false;
            }
        } catch (SupabaseAuthClient.CredentialsRejectedException
                | SupabaseAuthClient.InvalidAuthResponseException
                | JwtException
                | OAuth2AuthenticationException
                | IllegalArgumentException exception) {
            // Invalid or revoked refresh credentials fail closed.
        }

        SecurityContextHolder.clearContext();
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // A concurrent request may already have invalidated this session.
        }
        return true;
    }

    private static boolean shouldRefresh(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        return expiresAt == null || !expiresAt.isAfter(Instant.now().plus(REFRESH_BEFORE_EXPIRY));
    }

    private static boolean isExpired(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        return expiresAt == null || !expiresAt.isAfter(Instant.now());
    }
}
