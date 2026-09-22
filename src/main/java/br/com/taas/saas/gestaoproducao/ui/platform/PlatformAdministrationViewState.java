package br.com.taas.saas.gestaoproducao.ui.platform;

import java.util.List;

import br.com.taas.saas.gestaoproducao.platform.administration.application.invitation.InvitationAdministrationView;
import br.com.taas.saas.gestaoproducao.platform.administration.application.membership.PlatformMembershipAdministrationView;
import br.com.taas.saas.gestaoproducao.platform.identity.model.Tenant;

public record PlatformAdministrationViewState(
        boolean platformAccess,
        boolean owner,
        List<Tenant> tenants,
        List<InvitationAdministrationView> invitations,
        PlatformMembershipAdministrationView membershipAdministration) {

    public PlatformAdministrationViewState {
        tenants = List.copyOf(tenants);
        invitations = List.copyOf(invitations);
    }

    public static PlatformAdministrationViewState denied() {
        return new PlatformAdministrationViewState(
                false,
                false,
                List.of(),
                List.of(),
                new PlatformMembershipAdministrationView(List.of(), List.of()));
    }
}
