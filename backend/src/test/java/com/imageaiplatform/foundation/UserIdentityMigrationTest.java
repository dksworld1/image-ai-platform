package com.imageaiplatform.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class UserIdentityMigrationTest {
    @Test
    void preservesLegacyKeysRolesAuditValuesAndUnknownRealNames() {
        var old = Flyway.configure().dataSource("jdbc:h2:mem:identity-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration/app").target("6").load();
        old.migrate();
        var source = old.getConfiguration().getDataSource();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO auth_user (username, password_hash, created_by, updated_by) VALUES ('legacy-login', 'unchanged', 'legacy-login', '0')");
        jdbc.update("INSERT INTO auth_user (username, password_hash) VALUES ('second-login', 'second')");
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('reader', 'Reader')");
        jdbc.update("INSERT INTO auth_user_role (username, role_code, created_by) VALUES ('legacy-login', 'reader', 'legacy-login')");
        jdbc.update("INSERT INTO dataset (dataset_id, name, created_by, updated_by) VALUES ('old-dataset', 'old', 'legacy-login', '0')");
        var before = jdbc.queryForMap("SELECT created_at, updated_at FROM auth_user WHERE username='legacy-login'");
        var current = Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common/identity").target("10").load();
        assertThat(current.migrate().migrationsExecuted).isEqualTo(4);
        var user = jdbc.queryForMap("SELECT * FROM auth_user WHERE user_key='legacy-login'");
        assertThat(user.get("USER_ID")).isEqualTo("legacy-login");
        assertThat(user.get("USERNAME")).isNull();
        assertThat(user.get("PASSWORD_HASH")).isEqualTo("unchanged");
        assertThat(user.get("CREATED_AT")).isEqualTo(before.get("CREATED_AT"));
        assertThat(user.get("UPDATED_AT")).isEqualTo(before.get("UPDATED_AT"));
        assertThat(user.get("CREATED_BY")).isEqualTo("legacy-login");
        assertThat(jdbc.queryForObject("SELECT user_key FROM auth_user_role WHERE role_code='reader'", String.class)).isEqualTo("legacy-login");
        assertThat(jdbc.queryForObject("SELECT created_by FROM dataset WHERE dataset_id='old-dataset'", String.class)).isEqualTo("legacy-login");
        assertThat(jdbc.queryForObject("SELECT updated_by FROM dataset WHERE dataset_id='old-dataset'", String.class)).isEqualTo("0");
        assertThat(jdbc.queryForObject("SELECT user_id FROM auth_user WHERE user_key='second-login'", String.class)).isEqualTo("second-login");
        assertThat(current.migrate().migrationsExecuted).isZero();
    }
}
