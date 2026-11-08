package com.imageaiplatform.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class ExternalConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void loadsJdbcAndAuthFromAdditionalLocations(String profile, @TempDir Path directory) throws Exception {
        Path jdbc = Files.writeString(directory.resolve("jdbc.yaml"), "datasource.app.username: external-user");
        Path auth = Files.writeString(directory.resolve("auth.yaml"), "app.auth.jwt.secret: test-only-key");
        try (var context = new SpringApplicationBuilder(ExternalConfigurationTest.class)
                .web(WebApplicationType.NONE).run("--spring.profiles.active=" + profile,
                        "--spring.config.additional-location=" + jdbc.toUri() + "," + auth.toUri())) {
            assertThat(context.getEnvironment().getProperty("datasource.app.username")).isEqualTo("external-user");
            assertThat(context.getEnvironment().getProperty("app.auth.jwt.secret")).isEqualTo("test-only-key");
        }
    }
}
