package br.com.taas.saas.gestaoproducao.platform.access.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SupabaseJwtProperties.class)
public class SupabaseResourceServerSecurityConfiguration {

    @Bean
    RestOperations supabaseJwkSetRestOperations(SupabaseJwtProperties properties) {
        RestTemplate restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
        ClientHttpRequestInterceptor apiKeyInterceptor =
                new SupabaseJwkSetApiKeyInterceptor(properties.publishableKey());
        restTemplate.getInterceptors().add(apiKeyInterceptor);
        return restTemplate;
    }

    @Bean
    JwtDecoder supabaseJwtDecoder(
            SupabaseJwtProperties properties,
            RestOperations supabaseJwkSetRestOperations) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.jwkSetUri())
                .restOperations(supabaseJwkSetRestOperations)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
        decoder.setJwtValidator(SupabaseJwtValidators.create(properties.issuer(), properties.audience()));
        return decoder;
    }

    @Bean
    SecurityFilterChain resourceServerSecurityFilterChain(
            HttpSecurity http,
            SupabaseJwtAuthenticationConverter authenticationConverter) throws Exception {
        http
                .with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin
                        .enableAuthorizedRequestsConfiguration(false))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/login").permitAll()
                        .requestMatchers(VaadinSecurityConfigurer.getDefaultHttpSecurityPermitMatcher())
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter)));
        return http.build();
    }
}
