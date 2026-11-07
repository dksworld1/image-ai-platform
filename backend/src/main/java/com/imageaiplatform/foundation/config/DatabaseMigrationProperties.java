package com.imageaiplatform.foundation.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.migration")
public record DatabaseMigrationProperties(List<String> appLocations, List<String> auditLocations) {
    public DatabaseMigrationProperties {
        appLocations = appLocations == null ? List.of() : List.copyOf(appLocations);
        auditLocations = auditLocations == null ? List.of() : List.copyOf(auditLocations);
    }
}
