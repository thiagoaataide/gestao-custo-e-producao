package br.com.taas.saas.gestaoproducao.platform.administration.config;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.invitation")
public record InvitationLinkProperties(String baseUrl) {

    public InvitationLinkProperties {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        baseUrl = baseUrl.trim();
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        URI origin;
        try {
            origin = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("baseUrl must be a valid HTTP(S) origin", exception);
        }
        if (!origin.isAbsolute()
                || origin.getHost() == null
                || !("http".equalsIgnoreCase(origin.getScheme())
                        || "https".equalsIgnoreCase(origin.getScheme()))
                || origin.getRawUserInfo() != null
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null
                || (origin.getRawPath() != null && !origin.getRawPath().isEmpty())) {
            throw new IllegalArgumentException("baseUrl must be an HTTP(S) origin without path or credentials");
        }
    }

    public void requirePublicOrigin() {
        URI origin = URI.create(baseUrl);
        if (!"https".equalsIgnoreCase(origin.getScheme())) {
            throw new IllegalArgumentException("published invitation origin must use HTTPS");
        }
        String host = origin.getHost().toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if ("localhost".equals(host) || host.endsWith(".localhost")) {
            throw new IllegalArgumentException("published invitation origin must not use localhost");
        }
        if (!host.contains(".") && !host.contains(":")) {
            throw new IllegalArgumentException("published invitation origin must use a public hostname");
        }
        if (isNonPublicAddressLiteral(host)) {
            throw new IllegalArgumentException("published invitation origin must not use a local or private address");
        }
    }

    public String linkFor(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");
        if (rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }
        return baseUrl + "/invitations/" + rawToken;
    }

    private static boolean isNonPublicAddressLiteral(String host) {
        String literal = host;
        if (literal.startsWith("[") && literal.endsWith("]")) {
            literal = literal.substring(1, literal.length() - 1);
        }
        if (!literal.contains(":") && !literal.matches("[0-9.]+")) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(literal);
            byte[] bytes = address.getAddress();
            boolean ipv6UniqueLocal = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()
                    || ipv6UniqueLocal;
        } catch (UnknownHostException exception) {
            return true;
        }
    }
}
