package com.imageaiplatform.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class CommonAuditColumnsTest {
    @Test
    void upgradesExistingLegacyProbesWithTheDatabaseSpecificScripts() {
        for (String database : new String[] {"catalog", "archive"}) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:legacy-audit-" + UUID.randomUUID(), "sa", "");
            // DB를 여러 연결에서 유지한 뒤 닫을 수 있도록 연결을 열어 둡니다.
            try (var connection = source.getConnection()) {
                var jdbc = new JdbcTemplate(source);
                jdbc.execute("CREATE TABLE foundation_probe (id BIGINT PRIMARY KEY, probe_value VARCHAR(100) NOT NULL)");
                jdbc.update("INSERT INTO foundation_probe (id, probe_value) VALUES (1, 'keep')");
                new ResourceDatabasePopulator(new ClassPathResource(
                        "db/migration/" + database + "/V1__add_common_audit_columns.sql")).execute(source);
                var row = jdbc.queryForMap("SELECT * FROM foundation_probe WHERE id=1");
                assertThat(row.get("PROBE_VALUE")).isEqualTo("keep");
                assertThat(row.get("CREATED_AT")).isNotNull();
                assertThat(row.get("UPDATED_AT")).isNotNull();
                assertThat(row.get("CREATED_BY")).isEqualTo("0");
                assertThat(row.get("UPDATED_BY")).isEqualTo("0");
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    @Test
    void upgradesExistingRowsAndReservesSystemActorWithoutCreatingAnAccount() {
        var old = Flyway.configure()
                .dataSource("jdbc:h2:mem:common-columns-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration/app").target("4").load();
        old.migrate();
        var source = old.getConfiguration().getDataSource();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO auth_user (username, password_hash) VALUES ('member', 'unchanged')");
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('READER', 'Reader')");
        jdbc.update("INSERT INTO auth_permission (permission_code, display_name) VALUES ('dataset:item:read', 'Read')");
        jdbc.update("INSERT INTO auth_user_role (username, role_code) VALUES ('member', 'READER')");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('READER', 'dataset:item:read')");

        var current = Flyway.configure().dataSource(source).locations("classpath:db/migration/app", "classpath:db/seed/app/common/identity").load();
        var before = OffsetDateTime.now().minusSeconds(1).toInstant();
        assertThat(current.migrate().migrationsExecuted).isEqualTo(5);
        var after = OffsetDateTime.now().plusSeconds(1).toInstant();
        for (String table : new String[] {"auth_user", "auth_role", "auth_permission", "auth_user_role", "auth_role_permission"}) {
            var audit = jdbc.queryForMap("SELECT created_at, updated_at, created_by, updated_by FROM " + table);
            assertThat(((OffsetDateTime) audit.get("CREATED_AT")).toInstant()).isBetween(before, after);
            assertThat(((OffsetDateTime) audit.get("UPDATED_AT")).toInstant()).isBetween(before, after);
            assertThat(audit.get("CREATED_BY")).isEqualTo("0");
            assertThat(audit.get("UPDATED_BY")).isEqualTo("0");
        }
        assertThat(jdbc.queryForObject("SELECT password_hash FROM auth_user WHERE user_key='member'", String.class))
                .isEqualTo("unchanged");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_user WHERE user_key='0'", Integer.class)).isZero();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO auth_user (user_key, user_id, password_hash) VALUES ('0', 'reserved', 'unused')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (String column : new String[] {"created_at", "updated_at", "created_by", "updated_by"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE auth_user SET " + column + " = NULL WHERE user_key='member'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(current.migrate().migrationsExecuted).isZero();
    }
}
