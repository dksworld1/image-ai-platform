package com.imageaiplatform.foundation.config;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
public class FlywayConfiguration {
    @Bean
    FlywayMigrationStrategy appFlywayMigrationStrategy(Environment environment) {
        boolean migrationOnly = environment.getProperty("app.database-migration-only", Boolean.class, false);
        boolean controlledEnvironment = environment.acceptsProfiles(Profiles.of("dev", "prod"));

        return flyway -> {
            if (!controlledEnvironment || migrationOnly) {
                flyway.migrate();
            }
        };
    }
}
