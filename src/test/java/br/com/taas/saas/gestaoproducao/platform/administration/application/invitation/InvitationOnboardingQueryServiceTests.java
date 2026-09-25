package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.InvitationRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.TenantRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Invitation;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.InvitationTokenDigest;
import br.com.taas.saas.gestaoproducao.platform.identity.model.MembershipRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.NormalizedEmail;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;
import br.com.taas.saas.gestaoproducao.platform.identity.model.TenantStatus;

class InvitationOnboardingQueryServiceTests {

    private static final String TOKEN = "valid-bearer-token";
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("12345678-1234-1234-1234-123456789012");

    @Test
    void onlyAValidPendingInvitationRevealsTheReadOnlyRecipientAddress() {
        InvitationRepository repository = mock(InvitationRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        when(repository.findPendingByToken(TOKEN, NOW)).thenReturn(Optional.of(invitation()));
        when(tenants.findById(TENANT_ID)).thenReturn(Optional.of(
                new Tenant(TENANT_ID, TenantStatus.ACTIVE, NOW.minusSeconds(3600))));
        InvitationOnboardingQueryService service = new InvitationOnboardingQueryService(repository, tenants);

        assertThat(service.invitedEmail(TOKEN, NOW)).contains("invitee@outlook.com");
        assertThat(service.invitedEmail("", NOW)).isEmpty();
        assertThat(service.invitedEmail(TOKEN, null)).isEmpty();
    }

    @Test
    void suspendedTenantInvitationDoesNotExposeAnAddressForSignup() {
        InvitationRepository repository = mock(InvitationRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        when(repository.findPendingByToken(TOKEN, NOW)).thenReturn(Optional.of(invitation()));
        when(tenants.findById(TENANT_ID)).thenReturn(Optional.of(
                new Tenant(TENANT_ID, TenantStatus.SUSPENDED, NOW.minusSeconds(3600))));
        InvitationOnboardingQueryService service = new InvitationOnboardingQueryService(repository, tenants);

        assertThat(service.invitedEmail(TOKEN, NOW)).isEmpty();
    }

    @Test
    void expiredOrMissingInvitationDoesNotExposeAnAddressForSignup() {
        InvitationRepository repository = mock(InvitationRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        when(repository.findPendingByToken(TOKEN, NOW)).thenReturn(Optional.empty());
        InvitationOnboardingQueryService service = new InvitationOnboardingQueryService(repository, tenants);

        assertThat(service.invitedEmail(TOKEN, NOW)).isEmpty();
    }

    private static Invitation invitation() {
        Instant createdAt = NOW.minusSeconds(120);
        return new Invitation(
                UUID.randomUUID(),
                TENANT_ID,
                NormalizedEmail.from("Invitee@Outlook.com"),
                MembershipRole.TENANT_USER,
                InvitationStatus.PENDING,
                InvitationTokenDigest.fromToken(TOKEN),
                null,
                NOW.plusSeconds(3600),
                null,
                null,
                UUID.randomUUID(),
                createdAt);
    }
}
