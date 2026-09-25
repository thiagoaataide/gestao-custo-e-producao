# ADR-026: Cadastro Supabase iniciado por convite

- **Status:** Accepted
- **Date:** 2026-09-25
- **Related decisions:** `ADR-015`, `ADR-025`, `AD-011`

## Context

F-01 currently accepts invitations only after the recipient has authenticated
with a Supabase Auth account whose verified email matches the invitation. A
recipient without such an account cannot complete the flow. The current Vaadin
authentication adapter supports password sign-in, refresh, and logout, but not
sign-up or email verification. A logged-in administrator account can also be
different from the invite recipient's account.

## Decision

- The application offers registration only while continuing a valid,
  unexpired tenant invitation; the login page does not gain a general signup
  flow. Supabase Auth may still contain an account created outside the app, but
  such an account receives no application identity or tenant access without
  completing a matching invitation.
- The invitation email is the registration address, is shown as read-only, and
  must be verified before acceptance. Passwords remain exclusively managed by
  Supabase Auth.
- Registration creates a Supabase Auth user. It does not create a domain
  `ExternalIdentity`, membership, or tenant access by itself.
- After verification, the application returns to the same invitation. The
  recipient must explicitly accept it. The application then atomically
  associates the verified Supabase subject with the domain identity, activates
  the membership, marks the invitation accepted, and writes the audit event.
- An authenticated session whose verified email does not match the invitation
  cannot accept it. The flow must preserve the invitation while offering a
  safe way to sign out and register/sign in as the invited address; it must not
  silently use the current Gmail identity for an Outlook invitation.
- Every invitation recipient who needs email verification must receive and
  enter a one-time code (OTP) before accepting, regardless of email provider or
  domain. There are no provider-specific exceptions or domain allowlists.
  Existing Auth identities may proceed when Supabase already reports their
  invitation email as verified; unverified identities complete the same OTP
  verification before acceptance.
- OTP is selected because Supabase documents that Microsoft Safe Links may
  consume one-click confirmation URLs before the recipient uses them.
- A user with an Auth account but no active membership remains blocked from
  tenant operations. No signup path creates a tenant. The app's invitation
  guard is an authorization boundary; it must not be described as preventing
  direct account creation against the public Auth provider API.

## Alternatives considered

### General public registration in the application

Not adopted. A general signup screen would create an application onboarding
path without platform provisioning. The invitation screen remains the only
registration entry point provided by the product; an Auth account created
outside it has no domain authorization.

### Requiring a pre-existing Supabase account

Not adopted. It prevents a valid invited person from onboarding without
administrator intervention.

### One-click confirmation link

Not adopted. Email security scanners can follow links before the recipient
does, consuming a one-time verification action; a domain-neutral OTP flow is
used for all unverified invitation recipients.

## Consequences

- F-01 must add a public, invitation-scoped registration and verification
  journey, distinct from the normal login page.
- Invite context must survive account switching and email verification without
  placing the raw invitation token in logs or audit events.
- Supabase Auth email delivery must be configured and verified before this
  journey can be accepted in production. The default hosted sender is not a
  production delivery commitment; use a configured SMTP provider.
- Auth-user creation can succeed while later domain acceptance fails. In that
  case the Auth account remains without tenant authorization and the invitation
  remains available until it expires or is revoked; acceptance remains
  transactional inside the application database.

## References

- [Supabase Auth password-based authentication](https://supabase.com/docs/guides/auth/passwords)
- [Supabase Auth email templates and OTP guidance](https://supabase.com/docs/guides/auth/auth-email-templates)
- [Supabase Auth API](https://supabase.com/docs/reference/self-hosting-auth)
