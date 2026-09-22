package br.com.taas.saas.gestaoproducao.platform.administration.application.membership;

import java.util.List;

public record PlatformMembershipAdministrationView(
        List<PlatformRoleAdministrationView> platformRoles,
        List<MembershipAdministrationView> memberships) {

    public PlatformMembershipAdministrationView {
        platformRoles = List.copyOf(platformRoles);
        memberships = List.copyOf(memberships);
    }
}
