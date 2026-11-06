package com.imageaiplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.imageaiplatform.auth.dao.EffectivePermissionDao;
import com.imageaiplatform.auth.service.AuthorizationService;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.datasource.url=jdbc:h2:mem:auth-rbac-integration;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional("appTransactionManager")
class AuthorizationDatabaseTest {
    private static final String READ = "dataset:item:read";
    private static final String DELETE = "dataset:item:delete";

    @Autowired
    @Qualifier("appDataSource")
    private DataSource appDataSource;

    @Autowired
    private EffectivePermissionDao permissionDao;

    @Autowired
    private AuthorizationService authorizationService;

    private JdbcTemplate jdbc;

    @BeforeEach
    void createAssignments() {
        jdbc = new JdbcTemplate(appDataSource);
        jdbc.update("INSERT INTO auth_user (username, password_hash) VALUES (?, ?)", "rbac-user", "test-only");
        jdbc.update("INSERT INTO auth_user (username, password_hash) VALUES (?, ?)", "empty-user", "test-only");
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('READER', 'Reader'), ('EDITOR', 'Editor')");
        jdbc.update("INSERT INTO auth_permission (permission_code, display_name) VALUES (?, 'Read'), (?, 'Delete')", READ, DELETE);
        jdbc.update("INSERT INTO auth_user_role (username, role_code) VALUES ('rbac-user', 'READER'), ('rbac-user', 'EDITOR')");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('READER', ?), ('EDITOR', ?), ('EDITOR', ?)", READ, READ, DELETE);
    }

    @Test
    void combinesMultipleRolesWithoutDuplicatePermissionsOrOtherUsersPermissions() {
        assertThat(permissionDao.findEffectivePermissionCodesByUsername("rbac-user"))
                .containsExactlyInAnyOrder(READ, DELETE);
        assertThat(permissionDao.findEffectivePermissionCodesByUsername("empty-user")).isEmpty();
        assertThat(permissionDao.findEffectivePermissionCodesByUsername("missing-user")).isEmpty();
        assertThat(permissionDao.findEffectivePermissionCodesByUsername("' OR '1'='1")).isEmpty();
    }

    @Test
    void wiredServiceReflectsAssignmentAndRolePermissionRevocation() {
        authorizationService.requirePermission("rbac-user", DELETE);
        jdbc.update("DELETE FROM auth_user_role WHERE username = 'rbac-user' AND role_code = 'EDITOR'");
        assertThatThrownBy(() -> authorizationService.requirePermission("rbac-user", DELETE))
                .isInstanceOf(AccessDeniedException.class);
        authorizationService.requirePermission("rbac-user", READ);
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code = 'READER'");
        assertThatThrownBy(() -> authorizationService.requirePermission("rbac-user", READ))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsInactiveMissingAndUnassignedAccounts() {
        authorizationService.requirePermission("rbac-user", READ);
        jdbc.update("UPDATE auth_user SET enabled = FALSE WHERE username = 'rbac-user'");
        assertThatThrownBy(() -> authorizationService.requirePermission("rbac-user", READ))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> authorizationService.requirePermission("missing-user", READ))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> authorizationService.requirePermission("empty-user", READ))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsDuplicateCodesAndAssignments() {
        for (String sql : new String[] {
                "INSERT INTO auth_role (role_code, display_name) VALUES ('READER', 'Duplicate')",
                "INSERT INTO auth_permission (permission_code, display_name) VALUES ('dataset:item:read', 'Duplicate')",
                "INSERT INTO auth_user_role (username, role_code) VALUES ('rbac-user', 'READER')",
                "INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('READER', 'dataset:item:read')"
        }) {
            assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void rejectsAllMissingParentReferences() {
        for (String sql : new String[] {
                "INSERT INTO auth_user_role (username, role_code) VALUES ('missing-user', 'READER')",
                "INSERT INTO auth_user_role (username, role_code) VALUES ('rbac-user', 'MISSING')",
                "INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('MISSING', 'dataset:item:read')",
                "INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('READER', 'missing:item:read')"
        }) {
            assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void restrictsParentDeletionAndKeyUpdatesUntilAssignmentsAreRemoved() {
        for (String sql : new String[] {
                "DELETE FROM auth_user WHERE username = 'rbac-user'",
                "DELETE FROM auth_role WHERE role_code = 'READER'",
                "DELETE FROM auth_permission WHERE permission_code = 'dataset:item:read'",
                "UPDATE auth_user SET username = 'renamed' WHERE username = 'rbac-user'",
                "UPDATE auth_role SET role_code = 'RENAMED' WHERE role_code = 'READER'",
                "UPDATE auth_permission SET permission_code = 'renamed:item:read' WHERE permission_code = 'dataset:item:read'"
        }) {
            assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
        }
        jdbc.update("DELETE FROM auth_user_role WHERE username = 'rbac-user'");
        assertThatCode(() -> jdbc.update("DELETE FROM auth_user WHERE username = 'rbac-user'"))
                .doesNotThrowAnyException();
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code = 'READER'");
        assertThatCode(() -> jdbc.update("DELETE FROM auth_role WHERE role_code = 'READER'"))
                .doesNotThrowAnyException();
        jdbc.update("DELETE FROM auth_role_permission WHERE permission_code = ?", READ);
        assertThatCode(() -> jdbc.update("DELETE FROM auth_permission WHERE permission_code = ?", READ))
                .doesNotThrowAnyException();
    }

    @Test
    void upgradesExistingV1DatabaseWithoutChangingAccountsOrSeedingRoles() {
        String url = "jdbc:h2:mem:auth-rbac-upgrade;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("1").load().migrate();
        var upgradeJdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(url, "sa", ""));
        upgradeJdbc.update("INSERT INTO auth_user (username, password_hash, enabled) VALUES ('existing-user', 'existing-hash', FALSE)");

        Flyway flyway = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgradeJdbc.queryForObject("SELECT password_hash FROM auth_user WHERE username = 'existing-user'", String.class))
                .isEqualTo("existing-hash");
        assertThat(upgradeJdbc.queryForObject("SELECT enabled FROM auth_user WHERE username = 'existing-user'", Boolean.class)).isFalse();
        assertThat(upgradeJdbc.queryForObject("SELECT COUNT(*) FROM auth_role", Integer.class)).isZero();
        assertThat(upgradeJdbc.queryForObject("SELECT COUNT(*) FROM auth_permission", Integer.class)).isZero();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
