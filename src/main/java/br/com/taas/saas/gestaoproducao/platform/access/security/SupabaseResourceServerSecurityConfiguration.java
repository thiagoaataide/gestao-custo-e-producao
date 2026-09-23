package br.com.taas.saas.gestaoproducao.platform.access.security;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

import br.com.taas.saas.gestaoproducao.ui.access.AuthenticationView;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SupabaseJwtProperties.class)
public class SupabaseResourceServerSecurityConfiguration {

    @Bean
    RestOperations supabaseJwkSetRestOperations(SupabaseJwtProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        ClientHttpRequestInterceptor apiKeyInterceptor =
                new SupabaseJwkSetApiKeyInterceptor(properties.publishableKey());
        restTemplate.getInterceptors().add(apiKeyInterceptor);
        return restTemplate;
    }

    @Bean
    HttpSessionSecurityContextRepository httpSessionSecurityContextRepository() {
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        repository.setDisableUrlRewriting(true);
        return repository;
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
            SupabaseJwtAuthenticationConverter authenticationConverter,
            SupabasePasswordAuthenticationProvider passwordAuthenticationProvider,
            SupabaseAuthClient authClient,
            SupabaseLocalLogoutHandler supabaseLogoutHandler,
            JwtDecoder jwtDecoder,
            HttpSessionSecurityContextRepository securityContextRepository) throws Exception {
        SupabaseSessionRefreshFilter sessionRefreshFilter = new SupabaseSessionRefreshFilter(
                authClient,
                jwtDecoder,
                authenticationConverter,
                securityContextRepository);
        http
                .with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin
                        .loginView(AuthenticationView.class)
                        .enableAuthorizedRequestsConfiguration(false)
                        .addLogoutHandler(supabaseLogoutHandler))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authenticationProvider(passwordAuthenticationProvider)
                .addFilterAfter(sessionRefreshFilter, BearerTokenAuthenticationFilter.class)
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
