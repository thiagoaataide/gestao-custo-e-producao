package br.com.taas.saas.gestaoproducao.platform.administration.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import br.com.taas.saas.gestaoproducao.platform.administration.application.port.out.InvitationDeliveryPort;
import br.com.taas.saas.gestaoproducao.platform.administration.config.SendGridInvitationDeliveryProperties;

class SendGridInvitationDeliveryConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    TestPropertiesConfiguration.class,
                    SendGridInvitationDeliveryConfiguration.class);

    @Test
    void disablesAdapterWhenDeliveryIsDisabledOrConfigurationIsIncomplete() {
        contextRunner
                .withPropertyValues(
                        "platform.invitation.delivery.enabled=false",
                        "platform.invitation.delivery.sendgrid.api-key=test-key",
                        "platform.invitation.delivery.sendgrid.from-email=sender@example.com")
                .run(context -> assertThat(context).doesNotHaveBean(InvitationDeliveryPort.class));

        contextRunner
                .withPropertyValues(
                        "platform.invitation.delivery.enabled=true",
                        "platform.invitation.delivery.sendgrid.api-key=test-key")
                .run(context -> assertThat(context).doesNotHaveBean(InvitationDeliveryPort.class));

        contextRunner
                .withPropertyValues(
                        "platform.invitation.delivery.enabled=true",
                        "platform.invitation.delivery.sendgrid.from-email=sender@example.com")
                .run(context -> assertThat(context).doesNotHaveBean(InvitationDeliveryPort.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SendGridInvitationDeliveryProperties.class)
    static class TestPropertiesConfiguration {
    }
}
