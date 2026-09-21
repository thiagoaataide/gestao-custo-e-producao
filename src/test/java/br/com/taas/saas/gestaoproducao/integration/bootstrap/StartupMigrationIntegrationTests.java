package br.com.taas.saas.gestaoproducao.integration.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import br.com.taas.saas.gestaoproducao.GestaoProducaoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class StartupMigrationIntegrationTests {

    @Test
    void startsWhenSchemaIsAlreadyUpdated() {
        try (ConfigurableApplicationContext first = startContext()) {
            assertThat(first.isActive()).isTrue();
        }

        try (ConfigurableApplicationContext second = startContext()) {
            assertThat(second.isActive()).isTrue();
        }
    }

    @Test
    void rejectsInvalidDatabaseConfigurationDuringStartup() {
        assertStartupFailure(
                "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/gestao_producao",
                "spring.flyway.url=jdbc:postgresql://127.0.0.1:1/gestao_producao");
    }

    @Test
    void rejectsBrokenMigrationDuringStartup() {
        assertStartupFailure(
                "spring.flyway.locations=classpath:db/migration,classpath:db/migration-broken");
    }

    private void assertStartupFailure(String... properties) {
        Throwable failure = catchThrowable(() -> {
            try (ConfigurableApplicationContext ignored = startContext(properties)) {
                throw new AssertionError("Application startup unexpectedly succeeded");
            }
        });

        assertThat(failure)
            .isNotNull()
            .isNotInstanceOf(AssertionError.class);
    }

    private ConfigurableApplicationContext startContext(String... properties) {
        String[] commandLineProperties = new String[properties.length + 1];
        commandLineProperties[0] = "--server.port=0";
        for (int index = 0; index < properties.length; index++) {
            commandLineProperties[index + 1] = "--" + properties[index];
        }

        return new SpringApplicationBuilder(GestaoProducaoApplication.class)
            .web(WebApplicationType.SERVLET)
            .profiles("test")
            .run(commandLineProperties);
    }
}
