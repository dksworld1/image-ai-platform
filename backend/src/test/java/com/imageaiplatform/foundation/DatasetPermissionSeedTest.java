package com.imageaiplatform.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class DatasetPermissionSeedTest {
    @Test
    void addsMissingPermissionsWithoutChangingExistingDefinitionsGrantsOrAuditValues() {
        var schema = Flyway.configure()
                .dataSource("jdbc:h2:mem:dataset-permissions-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common/identity").load();
        schema.migrate();
        var source = schema.getConfiguration().getDataSource();
        var jdbc = new JdbcTemplate(source);
        var original = OffsetDateTime.parse("2025-01-01T09:30:25.123456+09:00");
        jdbc.update("INSERT INTO auth_permission (permission_code, display_name, description, created_at, updated_at, created_by, updated_by) "
                + "VALUES ('dataset:item:read', '기존 조회 권한', '기존 설명', ?, ?, 'original', 'editor')", original, original);
        jdbc.update("INSERT INTO auth_permission (permission_code, display_name) VALUES ('other:item:read', '기존 다른 권한')");
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('reader', 'Reader')");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('reader', 'dataset:item:read')");
        var existing = jdbc.queryForList("SELECT * FROM auth_permission ORDER BY permission_code");
        var grants = jdbc.queryForList("SELECT * FROM auth_role_permission");

        var common = Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common").load();
        common.migrate();
        assertThat(jdbc.queryForList("SELECT permission_code FROM auth_permission ORDER BY permission_code", String.class))
                .containsExactly("dataset:item:create", "dataset:item:delete", "dataset:item:read",
                        "dataset:item:update", "dataset:item:use", "other:item:read");
        assertThat(jdbc.queryForList("SELECT * FROM auth_permission WHERE permission_code IN ('dataset:item:read', 'other:item:read') ORDER BY permission_code"))
                .isEqualTo(existing);
        assertThat(jdbc.queryForList("SELECT * FROM auth_role_permission")).isEqualTo(grants);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_user", Integer.class)).isZero();
        for (var row : jdbc.queryForList("SELECT * FROM auth_permission WHERE permission_code NOT IN ('dataset:item:read', 'other:item:read')")) {
            assertThat(row.get("DISPLAY_NAME")).isNotNull();
            assertThat(row.get("DESCRIPTION")).isNotNull();
            assertThat(row.get("CREATED_AT")).isInstanceOf(OffsetDateTime.class).isEqualTo(row.get("UPDATED_AT"));
            assertThat(row.get("CREATED_BY")).isEqualTo("0");
            assertThat(row.get("UPDATED_BY")).isEqualTo("0");
        }

        var seeded = jdbc.queryForList("SELECT * FROM auth_permission ORDER BY permission_code");
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/app/common/R__03_dataset_permissions.sql"))
                .execute(source);
        assertThat(jdbc.queryForList("SELECT * FROM auth_permission ORDER BY permission_code")).isEqualTo(seeded);
        assertThat(jdbc.queryForList("SELECT * FROM auth_role_permission")).isEqualTo(grants);
        assertThat(common.migrate().migrationsExecuted).isZero();
    }
}
