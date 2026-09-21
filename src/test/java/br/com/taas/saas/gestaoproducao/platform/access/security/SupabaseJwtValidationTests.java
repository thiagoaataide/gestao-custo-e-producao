package br.com.taas.saas.gestaoproducao.platform.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class SupabaseJwtValidationTests {

    private static final String ISSUER = "https://project.supabase.co/auth/v1";
    private static final String AUDIENCE = "authenticated";

    private ECKey signingKey;
    private NimbusJwtDecoder decoder;

    @BeforeEach
    void setUp() throws JOSEException {
        signingKey = new ECKeyGenerator(Curve.P_256).keyID("test-key").generate();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK()));
        decoder = NimbusJwtDecoder.withJwkSource(source)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
        decoder.setJwtValidator(SupabaseJwtValidators.create(ISSUER, AUDIENCE));
    }

    @Test
    void acceptsValidEs256TokenWithExpectedIssuerTimeAndAudience() throws Exception {
        Jwt decoded = decoder.decode(token(ISSUER, AUDIENCE, "user-123", Instant.now().plusSeconds(300), signingKey));

        assertThat(decoded.getSubject()).isEqualTo("user-123");
        assertThat(decoded.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(decoded.getAudience()).containsExactly(AUDIENCE);
    }

    @Test
    void rejectsTokenWithWrongIssuer() throws Exception {
        assertThatThrownBy(() -> decoder.decode(
                token("https://other.supabase.co/auth/v1", AUDIENCE, "user-123", Instant.now().plusSeconds(300), signingKey)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        assertThatThrownBy(() -> decoder.decode(
                token(ISSUER, AUDIENCE, "user-123", Instant.now().minusSeconds(300), signingKey)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenWithUnexpectedAudience() throws Exception {
        assertThatThrownBy(() -> decoder.decode(
                token(ISSUER, "unexpected", "user-123", Instant.now().plusSeconds(300), signingKey)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        ECKey otherSigningKey = new ECKeyGenerator(Curve.P_256).keyID("test-key").generate();

        assertThatThrownBy(() -> decoder.decode(
                token(ISSUER, AUDIENCE, "user-123", Instant.now().plusSeconds(300), otherSigningKey)))
                .isInstanceOf(JwtException.class);
    }

    private static String token(
            String issuer,
            String audience,
            String subject,
            Instant expiration,
            ECKey key) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .issueTime(Date.from(Instant.now().minusSeconds(30)))
                .expirationTime(Date.from(expiration))
                .build();
        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(),
                claims);
        signedJwt.sign(new ECDSASigner(key.toECPrivateKey()));
        return signedJwt.serialize();
    }
}
