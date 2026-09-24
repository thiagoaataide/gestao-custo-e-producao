# F-01 closure: T20 and T22

Sources:

- `.specs/features/f-01-platform-provisioning/tasks.md` - T20/T22 scope, acceptance, test gate and manual UAT boundary.
- `.specs/features/f-01-platform-provisioning/spec.md` - F01-09 and the provider-optional invitation contract.
- `.specs/features/f-01-platform-provisioning/design.md` - **binding for the integration boundary**: `InvitationDeliveryPort`, after-commit delivery, and platform provisioning flow.
- `AGENTS.md` - official-docs-only rule, shared PostgreSQL Compose lifecycle, secret handling and `mvnw.cmd clean verify` gate.
- Twilio SendGrid Mail Send API docs - endpoint/auth/request contract: https://www.twilio.com/docs/sendgrid/api-reference/mail-send/mail-send
- Twilio SendGrid free-trial docs - current no-cost limit and trial expiry: https://www.twilio.com/docs/sendgrid/ui/account-and-settings/upgrading-your-plan
- Spring Framework 7 REST clients and JDK request factory docs - synchronous HTTP client and request timeout support: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html

## Out of scope

- Real SendGrid credentials, actual email delivery, Render environment edits, deployment, or paid plan activation - these require owner-managed account configuration and must remain disabled until confirmed no-cost eligibility.
- F-02 and all operational tenant features - unrelated to F-01 closure.
- A SendGrid dependency in domain/application code, a new bounded context, or a microservice - the existing port is the integration seam.

## Landing

Add the vendor adapter only under F-01 integration and bind its configuration in the existing application setup. Reuse `InvitationDeliveryPort`, `InvitationDeliveryAfterCommitListener`, and the provider-neutral request; do not duplicate invite state or audit behavior.

| One-way door | Literal shape | Alternative rejected |
| --- | --- | --- |
| Hosted configuration contract for optional delivery | Existing `PLATFORM_INVITATION_DELIVERY_ENABLED=false` plus secret `PLATFORM_INVITATION_DELIVERY_SENDGRID_API_KEY` and `PLATFORM_INVITATION_DELIVERY_SENDGRID_FROM_EMAIL`; instantiate the adapter only when enabled and fully configured | Generic `SENDGRID_API_KEY` risks collision with other integrations; hardcoding credentials is unsafe |
| SendGrid external HTTP contract | `POST https://api.sendgrid.com/v3/mail/send`, `Authorization: Bearer <key>`, JSON Mail Send payload; HTTP 202 accepted; bounded 2s connect/3s read timeouts; no vendor type crosses the port | Adding a vendor SDK or putting provider calls in domain/application couples the product boundary to SendGrid |

- No schema, domain-state, or third-party dependency changes are required.

## Checks

### S1 - Optional SendGrid adapter · 5 checks

**C1** - When configured and enabled, the adapter makes exactly one HTTPS Mail Send v3 request with bearer authentication and the invitation recipient/link, and recognizes HTTP 202 as accepted.
Proof: `SendGridInvitationDeliveryAdapterTests.sendsInvitationUsingMailSendV3Contract`

**C2** - A provider rejection is surfaced as a provider-neutral delivery failure without exposing the API key, response body, recipient, or invitation link in exception/audit text.
Proof: `SendGridInvitationDeliveryAdapterTests.rejectsProviderErrorWithoutLeakingSensitiveData`

**C3** - An unavailable provider is surfaced as a provider-neutral delivery failure and does not leak transport details or credentials.
Proof: `SendGridInvitationDeliveryAdapterTests.handlesProviderUnavailableWithoutLeakingSensitiveData`

**C4** - Disabled or incomplete configuration does not register the SendGrid delivery port.
Proof: `SendGridInvitationDeliveryConfigurationTests.disablesAdapterWhenDeliveryIsDisabledOrConfigurationIsIncomplete`

**C5** - The delivery listener invokes the adapter only after database commit; a delivery failure is non-fatal and is audited without provider/PII payloads.
Proof: `InvitationDeliveryAfterCommitIntegrationTests.doesNotDeliverBeforeCommitAndDeliversAfterCommit`
Proof: `InvitationDeliveryAfterCommitIntegrationTests.deliveryFailureDoesNotThrowAfterCommitAndIsAudited`
Proof: `InvitationDeliveryAdapterTests.deliveryFailurePreservesRequestAndRecordsSafeAuditMetadata`

### S2 - Published invitation regression · 5 checks

**C6** - An unauthenticated visit to an invitation URL routes to login while preserving the same invitation return path; it does not accept the invitation.
Proof: `InvitationRouteSecurityIntegrationTests.unauthenticatedInviteRequestIsSavedAndSuccessfulLoginReturnsToTheSamePath`

**C7** - Opening an invitation route presents explicit confirmation without invoking acceptance; only clicking confirmation invokes the service with the validated session identity.
Proof: `InvitationAcceptanceViewTests.openingInvitationOnlyShowsExplicitConfirmationAndDoesNotExposeToken`
Proof: `InvitationAcceptanceViewTests.acceptsOnlyAfterExplicitConfirmationUsingValidatedSessionIdentity`

**C8** - The regression issues a public-origin invitation, accepts it only for the matching verified identity, activates one tenant membership, records acceptance audit, and establishes its tenant RLS context.
Proof: `PlatformProvisioningEndToEndIntegrationTests.publicOriginInvitationFlowCreatesLinkAndActivatesMembershipWithAudit`
Proof: `PlatformProvisioningEndToEndIntegrationTests.invitationAcceptanceCreatesOneMembershipAndRlsReceivesItsTenant`

**C9** - Rejected acceptance shows a generic message without disclosing the token, recipient, or tenant.
Proof: `InvitationAcceptanceViewTests.domainRejectionIsShownAsGenericMessageWithoutSensitiveDetails`

**C10** - The invitation link uses the configured public HTTPS origin and canonical `/invitations/{token}` path, never localhost in a published profile.
Proof: `PlatformProvisioningEndToEndIntegrationTests.publicOriginInvitationFlowCreatesLinkAndActivatesMembershipWithAudit`
Proof: `InvitationLinkPropertiesTests.publicHttpsOriginIsAccepted`
Proof: `InvitationLinkPropertiesTests.publishedOriginRejectsInsecureLocalAndPrivateOrigins`

## Swept

- validation: C1-C10; full gate `mvnw.cmd clean verify` and `git diff --check`
- failure modes: C2, C3, C5, C8
- idempotency: invitation acceptance/reissue remains under existing invitation rules; no provider retry or duplicate-send behavior is added
- authorization: invitation command and acceptance service retain existing platform/identity authorization; C6-C8
- concurrency/order: C5 proves delivery is after commit; existing invite/member database constraints remain unchanged
- data lifecycle: no provider credentials or invitation-token persistence added; audit excludes sensitive payload fields
- dependency failure: C2-C3; failure remains non-fatal after commit and link remains copyable
- state transitions: C5, C7-C8; delivery does not alter invitation or membership state
- observability: failed delivery audit uses only channel, delivery status, and exception type; no provider response body or secret

## Coverage

| Set (size) | Member -> proof | Unproven |
| --- | --- | --- |
| adapter configuration state (3) | enabled+complete C1 · disabled C4 · incomplete C4 | - |
| provider outcomes (3) | accepted C1 · rejection C2 · unavailable C3 | - |
| invitation flow stages (5) | emit C5 · public origin C10 · login return C6 · explicit confirmation C7 · active membership+audit C8 | - |
| protected invitation values (4) | API key C2-C3 · provider response C2-C3 · invitation link C2/C5 · recipient C2/C5 | - |

- Checks C1-C10 name test methods as required proofs; every named method must exist, run, and assert its stated outcome.
- No route, status-code, or response-shape claim is made here.
