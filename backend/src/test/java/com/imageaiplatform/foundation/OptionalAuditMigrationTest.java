package com.imageaiplatform.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.imageaiplatform.foundation.config.DatabaseMigrationConfiguration;

class OptionalAuditMigrationTest {
    @Test
    void skipsBothMigrationsWhenLocationsAreOmitted() {
        DataSource app = mock(DataSource.class);
        DataSource audit = mock(DataSource.class);
        new ApplicationContextRunner()
                .withUserConfiguration(DatabaseMigrationConfiguration.class)
                .withBean("appDataSource", DataSource.class, () -> app)
                .withBean("auditDataSource", DataSource.class, () -> audit)
                .withPropertyValues("app.migration.enabled=true",
                        "app.datasources.audit.url=jdbc:h2:mem:unused")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasBean("appFlyway").hasBean("auditFlyway");
                    verifyNoInteractions(app, audit);
                });
    }

    @Test
    void skipsAuditMigrationWhenLocationsAreOmitted() {
        DataSource audit = mock(DataSource.class);
        new ApplicationContextRunner()
                .withUserConfiguration(DatabaseMigrationConfiguration.class)
                .withBean("appDataSource", DataSource.class, () -> new DriverManagerDataSource(
                        "jdbc:h2:mem:optional-audit", "sa", ""))
                .withBean("auditDataSource", DataSource.class, () -> audit)
                .withPropertyValues("app.migration.enabled=true",
                        "app.migration.app-locations=classpath:db/migration/app",
                        "app.datasources.audit.url=jdbc:h2:mem:unused-audit")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasBean("appFlyway").hasBean("auditFlyway");
                    verifyNoInteractions(audit);
                });
    }
}
