package br.com.taas.saas.gestaoproducao.ui.platform;

import java.util.List;

import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAdministrationView;
import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.PlatformMembershipAdministrationView;
import br.com.taas.saas.gestaoproducao.platform.administration.application.operationalassignment.OperationalManagerAssignmentView;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;

public record PlatformAdministrationViewState(
        boolean platformAccess,
        boolean owner,
        List<Tenant> tenants,
        List<InvitationAdministrationView> invitations,
        PlatformMembershipAdministrationView membershipAdministration,
        List<OperationalManagerAssignmentView> operationalAssignments) {

    public PlatformAdministrationViewState {
        tenants = List.copyOf(tenants);
        invitations = List.copyOf(invitations);
        operationalAssignments = List.copyOf(operationalAssignments);
    }

    public PlatformAdministrationViewState(boolean platformAccess, boolean owner, List<Tenant> tenants,
            List<InvitationAdministrationView> invitations,
            PlatformMembershipAdministrationView membershipAdministration) {
        this(platformAccess, owner, tenants, invitations, membershipAdministration, List.of());
    }

    public static PlatformAdministrationViewState denied() {
        return new PlatformAdministrationViewState(
                false,
                false,
                List.of(),
                List.of(),
                new PlatformMembershipAdministrationView(List.of(), List.of()), List.of());
    }
}
