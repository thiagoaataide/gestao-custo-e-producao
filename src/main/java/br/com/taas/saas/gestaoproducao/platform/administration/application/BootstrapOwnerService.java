package br.com.taas.saas.gestaoproducao.platform.administration.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditAction;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditEvent;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditMetadata;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditResult;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.model.AuditTargetType;
import br.com.taas.saas.gestaoproducao.platform.administration.audit.port.out.AdministrativeAuditRepository;
import br.com.taas.saas.gestaoproducao.platform.administration.config.PlatformBootstrapProperties;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.ExternalIdentityRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.application.port.out.PlatformRoleRepository;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentity;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalIdentityStatus;
import br.com.taas.saas.gestaoproducao.platform.identity.model.ExternalSubject;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRole;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleAssignment;
import br.com.taas.saas.gestaoproducao.platform.identity.model.PlatformRoleStatus;

@Service
public class BootstrapOwnerService {

    private final PlatformRoleRepository platformRoleRepository;
    private final ExternalIdentityRepository externalIdentityRepository;
    private final AdministrativeAuditRepository administrativeAuditRepository;
    private final PlatformBootstrapProperties bootstrapProperties;

    public BootstrapOwnerService(
            PlatformRoleRepository platformRoleRepository,
            ExternalIdentityRepository externalIdentityRepository,
            AdministrativeAuditRepository administrativeAuditRepository,
            PlatformBootstrapProperties bootstrapProperties) {
        this.platformRoleRepository = Objects.requireNonNull(
                platformRoleRepository,
                "platformRoleRepository must not be null");
        this.externalIdentityRepository = Objects.requireNonNull(
                externalIdentityRepository,
                "externalIdentityRepository must not be null");
        this.administrativeAuditRepository = Objects.requireNonNull(
                administrativeAuditRepository,
                "administrativeAuditRepository must not be null");
        this.bootstrapProperties = Objects.requireNonNull(
                bootstrapProperties,
                "bootstrapProperties must not be null");
    }

    @Transactional
    public PlatformRoleAssignment bootstrapOwner(
            ExternalSubject authenticatedSubject,
            Instant occurredAt) {
        Objects.requireNonNull(authenticatedSubject, "authenticatedSubject must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");

        if (!bootstrapProperties.authorizes(authenticatedSubject)) {
            throw new PlatformBootstrapDeniedException();
        }

        var existingOwner = platformRoleRepository.findActiveOwner();
        if (existingOwner.isPresent()) {
            ExternalIdentity identity = existingIdentity(authenticatedSubject);
            if (!identity.isActive()
                    || !existingOwner.get().identityId().equals(identity.id())) {
                throw new PlatformBootstrapDeniedException();
            }
            recordSuccess(existingOwner.get(), identity.id(), occurredAt);
            return existingOwner.get();
        }

        ExternalIdentity identity = externalIdentityRepository
                .findByProviderAndExternalSubject(authenticatedSubject)
                .orElseGet(() -> externalIdentityRepository.save(new ExternalIdentity(
                        UUID.randomUUID(),
                        authenticatedSubject,
                        ExternalIdentityStatus.ACTIVE,
                        occurredAt)));

        if (!identity.isActive()) {
            throw new PlatformBootstrapDeniedException();
        }

        var owner = new PlatformRoleAssignment(
                UUID.randomUUID(),
                identity.id(),
                PlatformRole.PLATFORM_OWNER,
                PlatformRoleStatus.ACTIVE,
                occurredAt,
                null);
        var savedOwner = platformRoleRepository.save(owner);
        recordSuccess(savedOwner, identity.id(), occurredAt);
        return savedOwner;
    }

    private ExternalIdentity existingIdentity(ExternalSubject subject) {
        return externalIdentityRepository
                .findByProviderAndExternalSubject(subject)
                .orElseThrow(PlatformBootstrapDeniedException::new);
    }

    private void recordSuccess(
            PlatformRoleAssignment owner,
            UUID actorIdentityId,
            Instant occurredAt) {
        administrativeAuditRepository.save(new AuditEvent(
                UUID.randomUUID(),
                actorIdentityId,
                AuditAction.BOOTSTRAP_OWNER,
                AuditTargetType.PLATFORM_ROLE,
                owner.id(),
                AuditResult.SUCCESS,
                occurredAt,
                AuditMetadata.empty()));
    }
}
