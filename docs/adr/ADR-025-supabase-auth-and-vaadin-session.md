# ADR-025: Autenticação Supabase com sessão Vaadin no servidor

- **Status:** Accepted
- **Date:** 2026-09-23
- **Related decisions:** `ADR-015`, `ADR-016`, `ADR-017` and `ADR-023`

## Context

F-00 already validates Supabase access tokens as bearer tokens, but its Vaadin
login route is only a placeholder. A browser-held Supabase session does not
provide the server-side session expected by the Vaadin Flow application and
does not complete the first login to the existing platform-administration UI.

The V0 already separates external identity from tenant authorization. This
decision changes only how a browser establishes and maintains an authenticated
application session; it does not let Supabase claims choose a tenant or grant
platform roles.

## Decision

The Vaadin browser uses an email-and-password login form handled by Spring
Security form login. The application server sends those credentials to the
Supabase Auth password endpoint using the existing publishable key, validates
the returned access JWT with the configured issuer, audience, signature, and
expiration validators, and maps only its `sub` claim to `ExternalSubject`.

Spring Security stores the authenticated context in the server-side HTTP
session. The access and refresh tokens remain in that server-side context and
are never returned to browser JavaScript, rendered into the page, or written to
logs. The browser receives only the normal session cookie, configured as
HttpOnly, Secure in hosted HTTPS, and SameSite Lax. The idle session lifetime
is 30 minutes; no persistent “remember me” session is offered.

Before an access token expires, the server exchanges the current refresh token
with Supabase Auth, validates the new access token, verifies the same external
subject, and atomically replaces the access/refresh token pair in the session.
Refresh operations for one HTTP session are serialized so concurrent Vaadin
requests cannot reuse a one-time refresh token. A transient refresh failure
does not extend an expired access token: the current token may be used only
while it remains valid; once expired, the local session is invalidated and the
user must sign in again.

Logout invalidates the local Spring session and requests Supabase Auth logout
with the `local` scope. A provider failure must not keep the local application
session alive.

The V0 login page has no public registration or password-reset action. Account
creation and tenant/platform provisioning remain separate from authentication.
The existing Resource Server continues to validate bearer tokens for requests
that use that contract; it is not the browser's Vaadin session mechanism.

## Alternatives considered

### Supabase session in browser storage with bearer tokens

Not adopted for the Vaadin browser flow. It exposes access/refresh token
handling to client-side code and does not provide the server-side browser
session selected for the application UI. The existing Resource Server support
remains available for bearer-token requests.

### Application-managed passwords

Not adopted. The application does not persist or verify passwords and continues
to delegate identity authentication to Supabase Auth.

## Consequences

### Positive

- the currently deployed login placeholder becomes a real sign-in flow;
- Vaadin UI requests use the standard Spring Security session boundary;
- access and refresh tokens stay on the server and continue to feed the
  existing trusted-profile adapter;
- refresh-token rotation and local logout are explicit and testable;
- tenant membership, platform roles, and RLS remain application-owned.

### Negative

- an application instance restart loses its in-memory HTTP sessions, so users
  must sign in again; this is acceptable for the current single-instance,
  zero-cost MVP deployment;
- the server must handle refresh-token rotation and serialize refreshes per
  session;
- the browser login depends on a live Supabase Auth request.

## References

- [Spring Security 7.1.1: authentication persistence and session management](https://docs.spring.io/spring-security/reference/7.1/servlet/authentication/session-management.html)
- [Vaadin Flow: enabling security and form login](https://vaadin.com/docs/latest/flow/security/enabling-security)
- [Supabase Auth: password-based authentication](https://supabase.com/docs/guides/auth/passwords)
- [Supabase Auth API: password sign-in and refresh token](https://supabase.com/docs/reference/self-hosting-auth)
- [Supabase Auth: user sessions](https://supabase.com/docs/guides/auth/sessions)
- [Supabase Auth: sign out with local scope](https://supabase.com/docs/guides/auth/signout)
