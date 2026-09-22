package br.com.taas.saas.gestaoproducao.platform.administration.application.invitation;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public final class SecureInvitationTokenGenerator implements InvitationTokenGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
