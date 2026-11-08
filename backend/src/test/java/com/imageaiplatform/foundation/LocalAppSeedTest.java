package com.imageaiplatform.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LocalAppSeedTest {
    @Test
    void seedsAdminNavigationAndAllExistingPermissionsIdempotently() {
        var flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:local-seed-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration/app").load();
        flyway.migrate();
        var source = flyway.getConfiguration().getDataSource();
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_permission", Integer.class)).isZero();
        jdbc.update("INSERT INTO auth_permission (permission_code, display_name) VALUES ('dataset:item:read', 'Read')");
        var common = Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common").load();
        assertThat(common.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_user", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_role", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_screen", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_menu", Integer.class)).isEqualTo(4);
        var withSeed = Flyway.configure().dataSource(source)
                .locations("classpath:db/migration/app", "classpath:db/seed/app/common", "classpath:db/seed/app/local").load();
        assertThat(withSeed.migrate().migrationsExecuted).isEqualTo(1);

        String hash = jdbc.queryForObject("SELECT password_hash FROM auth_user WHERE username='admin'", String.class);
        assertThat(new BCryptPasswordEncoder().matches("admin", hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT enabled FROM auth_user WHERE username='admin'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForList("SELECT role_code FROM auth_user_role WHERE username='admin'", String.class))
                .containsExactly("admin");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_permission p WHERE NOT EXISTS "
                + "(SELECT 1 FROM auth_role_permission r WHERE r.role_code='admin' AND r.permission_code=p.permission_code)", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT route_path FROM ui_screen ORDER BY screen_code", String.class))
                .containsExactlyInAnyOrder("/", "/admin/screens", "/admin/menus");
        assertThat(jdbc.queryForObject("SELECT permission_required FROM ui_screen WHERE screen_code='home'", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_screen_permission", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_menu WHERE parent_menu_code='admin-menu'", Integer.class)).isEqualTo(2);
        assertThat(withSeed.migrate().migrationsExecuted).isZero();

        // SQL 자체의 재실행에서도 중복 없이 추가된 권한을 연결합니다.
        jdbc.update("INSERT INTO auth_permission (permission_code, display_name) VALUES ('dataset:item:write', 'Write')");
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/app/common/R__01_ui_permissions.sql"),
                new ClassPathResource("db/seed/app/common/R__02_ui_navigation.sql")).execute(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/seed/app/local/R__local_app_seed.sql")).execute(source);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_user", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_user_role", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_role_permission WHERE role_code='admin'", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_menu", Integer.class)).isEqualTo(4);
    }
}
