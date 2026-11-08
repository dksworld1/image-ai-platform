package com.imageaiplatform.foundation.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatabaseMigrationProperties.class)
@ConditionalOnProperty(name = "app.migration.enabled", havingValue = "true")
public class DatabaseMigrationConfiguration {
    @Bean
    Flyway appFlyway(@Qualifier("appDataSource") DataSource source,
            DatabaseMigrationProperties properties) {
        Flyway flyway = Flyway.configure().dataSource(source)
                .locations(properties.appLocations().toArray(String[]::new)).load();
        if (!properties.appLocations().isEmpty()) {
            flyway.migrate();
        }
        return flyway;
    }

    @Bean
    @ConditionalOnProperty("datasource.audit.url")
    Flyway auditFlyway(@Qualifier("auditDataSource") DataSource source,
            DatabaseMigrationProperties properties) {
        Flyway flyway = Flyway.configure().dataSource(source)
                .locations(properties.auditLocations().toArray(String[]::new)).load();
        if (!properties.auditLocations().isEmpty()) {
            flyway.migrate();
        }
        return flyway;
    }
}
