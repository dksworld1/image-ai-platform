package com.imageaiplatform.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.time.OffsetDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LocalAppSeedTest {
    @Test
    void removesExistingUiSchemaAndGrantsWithoutRemovingOtherPermissions() {
        var old = Flyway.configure()
                .dataSource("jdbc:h2:mem:retire-ui-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration/app").target("3").load();
        old.migrate();
        var source = old.getConfiguration().getDataSource();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('reader', 'Reader')");
        jdbc.update("INSERT INTO auth_permission (permission_code, display_name) VALUES "
                + "('ui:screen:manage', 'Screen'), ('ui:menu:manage', 'Menu'), ('dataset:item:read', 'Read')");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('reader', 'ui:screen:manage'), "
                + "('reader', 'ui:menu:manage'), ('reader', 'dataset:item:read')");
        jdbc.update("INSERT INTO ui_screen (screen_code, display_name, route_path) VALUES ('home', 'Home', '/')");
        jdbc.update("INSERT INTO ui_screen_permission VALUES ('home', 'ui:screen:manage')");
        jdbc.update("INSERT INTO ui_menu (menu_code, node_type, display_name, screen_code) "
                + "VALUES ('home', 'SCREEN', 'Home', 'home')");
        var current = Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common").load();
        current.migrate();
        assertThat(jdbc.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'", String.class))
                .doesNotContain("UI_SCREEN", "UI_MENU", "UI_SCREEN_PERMISSION");
        assertThat(jdbc.queryForList("SELECT permission_code FROM auth_permission", String.class))
                .containsExactlyInAnyOrder("dataset:item:read", "dataset:item:create", "dataset:item:use",
                        "dataset:item:update", "dataset:item:delete", "dataset:image:upload");
        assertThat(jdbc.queryForList("SELECT permission_code FROM auth_role_permission", String.class))
                .containsExactly("dataset:item:read");
        assertThat(current.migrate().migrationsExecuted).isZero();
    }

    @Test
    void seedsAdminAndAllExistingPermissionsIdempotently() {
        var flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:local-seed-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common/identity").target("10").load();
        flyway.migrate();
        var source = flyway.getConfiguration().getDataSource();
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_permission", Integer.class)).isZero();
        jdbc.update("INSERT INTO auth_permission (permission_code, display_name) VALUES ('dataset:item:read', 'Read')");
        var common = Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common").target("16").load();
        assertThat(common.migrate().migrationsExecuted).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_user", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_role", Integer.class)).isZero();
        var withSeed = Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common", "classpath:db/seed/app/local").load();
        assertThat(withSeed.migrate().migrationsExecuted).isEqualTo(5);

        String hash = jdbc.queryForObject("SELECT password_hash FROM auth_user WHERE user_id='admin'", String.class);
        String userKey = jdbc.queryForObject("SELECT user_key FROM auth_user WHERE user_id='admin'", String.class);
        var workspace = jdbc.queryForMap("SELECT * FROM workspace WHERE workspace_id='00000000-0000-4000-8000-000000000001'");
        var membership = jdbc.queryForMap("SELECT * FROM workspace_member WHERE workspace_id='00000000-0000-4000-8000-000000000001'");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace WHERE owner_user_key=?", Integer.class, userKey)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace_member WHERE user_key=? AND role_code='ADMIN' AND status='ACTIVE'", Integer.class, userKey)).isEqualTo(2);
        assertThat(workspace.get("WORKSPACE_ID")).isEqualTo("00000000-0000-4000-8000-000000000001");
        assertThat(workspace.get("NAME")).isEqualTo("공통 워크스페이스");
        assertThat(workspace.get("OWNER_USER_KEY")).isEqualTo(userKey);
        assertThat(workspace.get("STATUS")).isEqualTo("ACTIVE");
        assertThat(membership.get("WORKSPACE_ID")).isEqualTo(workspace.get("WORKSPACE_ID"));
        assertThat(membership.get("USER_KEY")).isEqualTo(userKey);
        assertThat(membership.get("ROLE_CODE")).isEqualTo("ADMIN");
        assertThat(membership.get("STATUS")).isEqualTo("ACTIVE");
        assertThat(membership.get("REQUESTED_AT")).isNull();
        assertThat(membership.get("REVIEWED_AT")).isNull();
        assertThat(membership.get("REVIEWED_BY")).isNull();
        assertThat(membership.get("JOINED_AT")).isEqualTo(membership.get("CREATED_AT"));
        for (var row : java.util.List.of(workspace, membership)) {
            assertThat(row.get("CREATED_AT")).isNotNull().isEqualTo(row.get("UPDATED_AT"));
            assertThat(row.get("CREATED_BY")).isEqualTo("0");
            assertThat(row.get("UPDATED_BY")).isEqualTo("0");
        }
        assertThat(UUID.fromString(userKey)).isNotNull();
        assertThat(new BCryptPasswordEncoder().matches("admin", hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT enabled FROM auth_user WHERE user_id='admin'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForList("SELECT role_code FROM auth_user_role WHERE user_key=(SELECT user_key FROM auth_user WHERE user_id='admin')", String.class))
                .containsExactly("admin");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_permission p WHERE NOT EXISTS "
                + "(SELECT 1 FROM auth_role_permission r WHERE r.role_code='admin' AND r.permission_code=p.permission_code)", Integer.class)).isZero();
        assertThat(withSeed.migrate().migrationsExecuted).isZero();
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code='admin' AND permission_code IN ('dataset:item:read', 'dataset:item:create', 'dataset:item:delete')");
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/app/local/R__local_workspace_seed.sql")).execute(source);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_role_permission WHERE role_code='admin' AND permission_code IN ('dataset:item:read', 'dataset:item:create', 'dataset:item:delete')", Integer.class)).isEqualTo(3);

        // SQL 자체의 재실행에서도 중복 없이 추가된 권한을 연결합니다.
        var originalCreated = OffsetDateTime.parse("2025-01-01T00:00:00Z");
        for (String table : new String[] {"auth_user", "auth_role", "auth_user_role", "auth_role_permission"}) {
            jdbc.update("UPDATE " + table + " SET created_at=?, updated_at=?, created_by='original', updated_by='original'",
                    originalCreated, originalCreated);
        }
        jdbc.update("INSERT INTO auth_permission (permission_code, display_name) VALUES ('dataset:item:write', 'Write')");
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/app/common/R__01_ui_permissions.sql"),
                new ClassPathResource("db/seed/app/common/R__02_ui_navigation.sql")).execute(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/app/local/R__local_app_seed.sql")).execute(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/app/local/R__local_workspace_seed.sql")).execute(source);
        assertThat(jdbc.queryForMap("SELECT * FROM workspace WHERE workspace_id='00000000-0000-4000-8000-000000000001'")).isEqualTo(workspace);
        assertThat(jdbc.queryForMap("SELECT * FROM workspace_member WHERE workspace_id='00000000-0000-4000-8000-000000000001'")).isEqualTo(membership);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workspace_member", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_user", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT user_key FROM auth_user WHERE user_id='admin'", String.class)).isEqualTo(userKey);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_user_role", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_role_permission WHERE role_code='admin'", Integer.class)).isEqualTo(7);
        for (String table : new String[] {"auth_user", "auth_role"}) {
            var audit = jdbc.queryForMap("SELECT created_at, updated_at, created_by, updated_by FROM " + table);
            assertThat(((OffsetDateTime) audit.get("CREATED_AT")).toInstant()).isEqualTo(originalCreated.toInstant());
            assertThat(audit.get("CREATED_BY")).isEqualTo("original");
            assertThat(((OffsetDateTime) audit.get("UPDATED_AT")).toInstant()).isAfter(originalCreated.toInstant());
            assertThat(audit.get("UPDATED_BY")).isEqualTo("0");
        }
        assertThat(jdbc.queryForObject("SELECT created_by FROM auth_user_role WHERE user_key=(SELECT user_key FROM auth_user WHERE user_id='admin')", String.class))
                .isEqualTo("original");
        assertThat(jdbc.queryForObject("SELECT created_by FROM auth_role_permission WHERE permission_code='dataset:item:read'", String.class))
                .isEqualTo("original");
        var added = jdbc.queryForMap("SELECT created_at, updated_at, created_by, updated_by FROM auth_role_permission "
                + "WHERE permission_code='dataset:item:write'");
        assertThat(added.get("CREATED_AT")).isEqualTo(added.get("UPDATED_AT"));
        assertThat(added.get("CREATED_BY")).isEqualTo("0");
        assertThat(added.get("UPDATED_BY")).isEqualTo("0");
    }
}
