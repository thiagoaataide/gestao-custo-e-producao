package br.com.taas.saas.gestaoproducao.platform.access.security;

import java.net.URI;
import java.util.Objects;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Calls only the end-user Supabase Auth endpoints needed by the Vaadin
 * session. The configured RestOperations adds the publishable {@code apikey}
 * and enforces short network timeouts.
 */
@Component
public final class SupabaseAuthClient {

    private static final String PASSWORD_GRANT = "password";
    private static final String REFRESH_GRANT = "refresh_token";

    private final RestOperations restOperations;
    private final String tokenEndpoint;
    private final String logoutEndpoint;
    private final String signupEndpoint;
    private final String verifyEndpoint;
    private final String resendEndpoint;

    @Autowired
    public SupabaseAuthClient(
            SupabaseJwtProperties properties,
            RestOperations restOperations) {
        this(restOperations, properties.issuer());
    }

    SupabaseAuthClient(RestOperations restOperations, String issuer) {
        this.restOperations = Objects.requireNonNull(restOperations);
        String base = requireText(issuer, "issuer");
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.tokenEndpoint = base + "/token";
        this.logoutEndpoint = base + "/logout?scope=local";
        this.signupEndpoint = base + "/signup";
        this.verifyEndpoint = base + "/verify";
        this.resendEndpoint = base + "/resend";
    }

    public SupabaseAuthSession signInWithPassword(String email, String password) {
        requireText(email, "email");
        requireText(password, "password");
        return exchangeToken(
                PASSWORD_GRANT,
                new PasswordSignInRequest(email.trim(), password));
    }

    public SupabaseAuthSession refresh(String refreshToken) {
        requireText(refreshToken, "refreshToken");
        return exchangeToken(REFRESH_GRANT, new RefreshRequest(refreshToken));
    }

    /** Creates only a Supabase Auth account; it never provisions local identity or membership. */
    public void signUpWithPassword(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        requireText(password, "password");
        try {
            ResponseEntity<SupabaseSignupResponse> response = restOperations.exchange(
                    URI.create(signupEndpoint),
                    HttpMethod.POST,
                    new HttpEntity<>(new SignupRequest(normalizedEmail, password)),
                    SupabaseSignupResponse.class);
            SupabaseSignupResponse body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful()
                    || body == null
                    || body.id() == null
                    || body.id().isBlank()
                    || body.email() == null
                    || !normalizeEmail(body.email()).equals(normalizedEmail)) {
                throw new InvalidAuthResponseException();
            }
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new SignupRejectedException();
            }
            throw new ProviderUnavailableException();
        } catch (RestClientException exception) {
            throw new ProviderUnavailableException();
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthResponseException();
        }
    }

    /** Re-sends the email signup OTP for an existing but unverified Auth account. */
    public void resendSignupEmailOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        try {
            ResponseEntity<Void> response = restOperations.exchange(
                    URI.create(resendEndpoint),
                    HttpMethod.POST,
                    new HttpEntity<>(new ResendSignupOtpRequest(normalizedEmail, "signup")),
                    Void.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ProviderUnavailableException();
            }
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new SignupRejectedException();
            }
            throw new ProviderUnavailableException();
        } catch (RestClientException exception) {
            throw new ProviderUnavailableException();
        }
    }

    /** Verifies the universal email OTP and returns the resulting provider session. */
    public SupabaseAuthSession verifyEmailOtp(String email, String otp) {
        String normalizedEmail = normalizeEmail(email);
        requireText(otp, "otp");
        try {
            ResponseEntity<SupabaseAuthResponse> response = restOperations.exchange(
                    URI.create(verifyEndpoint),
                    HttpMethod.POST,
                    new HttpEntity<>(new VerifyEmailOtpRequest(normalizedEmail, otp.trim(), "email")),
                    SupabaseAuthResponse.class);
            SupabaseAuthResponse body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful()
                    || body == null
                    || body.user() == null
                    || body.user().id() == null
                    || body.user().email() == null
                    || !normalizeEmail(body.user().email()).equals(normalizedEmail)
                    || body.user().emailConfirmedAt() == null
                    || body.user().emailConfirmedAt().isBlank()) {
                throw new InvalidAuthResponseException();
            }
            return new SupabaseAuthSession(
                    body.accessToken(), body.refreshToken(), body.user().id());
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == 400
                    || exception.getStatusCode().value() == 401
                    || exception.getStatusCode().value() == 422) {
                throw new OtpRejectedException();
            }
            throw new ProviderUnavailableException();
        } catch (RestClientException exception) {
            throw new ProviderUnavailableException();
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthResponseException();
        }
    }

    public void signOutLocal(String accessToken) {
        requireText(accessToken, "accessToken");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        try {
            restOperations.exchange(
                    URI.create(logoutEndpoint),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    Void.class);
        } catch (RestClientException exception) {
            throw new ProviderUnavailableException();
        }
    }

    private SupabaseAuthSession exchangeToken(String grantType, Object request) {
        try {
            ResponseEntity<SupabaseAuthResponse> response = restOperations.exchange(
                    URI.create(tokenEndpoint + "?grant_type=" + grantType),
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    SupabaseAuthResponse.class);
            SupabaseAuthResponse body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful()
                    || body == null
                    || body.user() == null
                    || body.user().id() == null
                    || body.user().id().isBlank()) {
                throw new InvalidAuthResponseException();
            }
            return new SupabaseAuthSession(
                    body.accessToken(),
                    body.refreshToken(),
                    body.user().id());
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == 400
                    || exception.getStatusCode().value() == 401) {
                throw new CredentialsRejectedException();
            }
            throw new ProviderUnavailableException();
        } catch (RestClientException exception) {
            throw new ProviderUnavailableException();
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String normalizeEmail(String email) {
        requireText(email, "email");
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public record SupabaseAuthSession(
            String accessToken,
            String refreshToken,
            String subject) {

        public SupabaseAuthSession {
            if (accessToken == null || accessToken.isBlank()
                    || refreshToken == null || refreshToken.isBlank()
                    || subject == null || subject.isBlank()) {
                throw new InvalidAuthResponseException();
            }
        }
    }

    public static final class CredentialsRejectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class ProviderUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class InvalidAuthResponseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class SignupRejectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class OtpRejectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private record PasswordSignInRequest(String email, String password) {
    }

    private record RefreshRequest(@JsonProperty("refresh_token") String refreshToken) {
    }

    private record SignupRequest(String email, String password) {
    }

    private record VerifyEmailOtpRequest(String email, String token, String type) {
    }

    private record ResendSignupOtpRequest(String email, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SupabaseSignupResponse(String id, String email) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SupabaseAuthResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            SupabaseUser user) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SupabaseUser(
            String id,
            String email,
            @JsonProperty("email_confirmed_at") String emailConfirmedAt) {
    }
}
