package com.imageaiplatform.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "app.migration.audit-locations=classpath:fixtures/audit-migration,classpath:fixtures/audit-seed",
        "datasource.app.url=jdbc:h2:file:./target/dual-db-test-${random.uuid}/app",
        "datasource.audit.url=jdbc:h2:file:./target/dual-db-test-${random.uuid}/audit"
})
@ActiveProfiles("test")
class DatabaseMigrationTest {
    @Autowired @Qualifier("appFlyway") Flyway app;
    @Autowired @Qualifier("auditFlyway") Flyway audit;

    @Test
    void appliesOnlyEachDatabasesScriptsAndSkipsAppliedVersions() {
        assertThat(app.info().applied()).anyMatch(migration -> migration.getVersion() != null
                && migration.getVersion().toString().equals("9"));
        assertThat(new JdbcTemplate(app.getConfiguration().getDataSource()).queryForList(
                "SELECT permission_code FROM auth_permission ORDER BY permission_code", String.class))
                .containsExactly("dataset:image:upload", "dataset:item:create", "dataset:item:delete", "dataset:item:read",
                        "dataset:item:update", "dataset:item:use");
        assertThat(audit.info().current().getVersion().toString()).isEqualTo("1");
        String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'";
        assertThat(new JdbcTemplate(app.getConfiguration().getDataSource()).queryForList(sql, String.class))
                .contains("AUTH_USER").doesNotContain("AUDIT_TEST_PROBE", "UI_SCREEN", "UI_MENU", "UI_SCREEN_PERMISSION");
        assertThat(new JdbcTemplate(audit.getConfiguration().getDataSource()).queryForList(sql, String.class))
                .contains("AUDIT_TEST_PROBE").doesNotContain("AUTH_USER");
        assertThat(app.migrate().migrationsExecuted).isZero();
        assertThat(new JdbcTemplate(audit.getConfiguration().getDataSource()).queryForObject(
                "SELECT COUNT(*) FROM audit_test_probe WHERE id = 7", Integer.class)).isEqualTo(1);
        assertThat(audit.migrate().migrationsExecuted).isZero();
    }
}
