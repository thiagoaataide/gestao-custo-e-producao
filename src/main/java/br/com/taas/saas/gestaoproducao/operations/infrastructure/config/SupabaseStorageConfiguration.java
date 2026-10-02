package br.com.taas.saas.gestaoproducao.operations.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SupabaseStorageProperties.class)
public class SupabaseStorageConfiguration {
    @Bean
    RestClient.Builder supabaseStorageRestClientBuilder() {
        return RestClient.builder();
    }
}
