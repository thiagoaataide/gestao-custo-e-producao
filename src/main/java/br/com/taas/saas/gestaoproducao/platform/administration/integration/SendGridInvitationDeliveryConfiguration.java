package br.com.taas.saas.gestaoproducao.platform.administration.integration;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.InvitationDeliveryPort;
import br.com.taas.saas.gestaoproducao.platform.administration.config.SendGridInvitationDeliveryProperties;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "platform.invitation.delivery",
        name = "enabled",
        havingValue = "true")
class SendGridInvitationDeliveryConfiguration {

    @Bean
    @Conditional(CompleteSendGridConfiguration.class)
    InvitationDeliveryPort sendGridInvitationDeliveryPort(
            SendGridInvitationDeliveryProperties properties) {
        return new SendGridInvitationDeliveryAdapter(createRestClient(), properties);
    }

    private static RestClient createRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    static final class CompleteSendGridConfiguration implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty(
                            "platform.invitation.delivery.sendgrid.api-key"))
                    && StringUtils.hasText(context.getEnvironment().getProperty(
                            "platform.invitation.delivery.sendgrid.from-email"));
        }
    }
}
