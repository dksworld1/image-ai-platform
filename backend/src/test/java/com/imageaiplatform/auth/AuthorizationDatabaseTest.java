package com.imageaiplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.imageaiplatform.auth.dao.AuthUserDao;
import com.imageaiplatform.auth.dao.EffectivePermissionDao;
import com.imageaiplatform.auth.service.AuthorizationService;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "datasource.app.url=jdbc:h2:mem:auth-rbac-integration;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Sql(scripts = "classpath:fixtures/auth-user.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS,
        config = @SqlConfig(dataSource = "appDataSource", transactionManager = "appTransactionManager"))
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
    private AuthUserDao userDao;

    @Autowired
    private AuthorizationService authorizationService;

    private JdbcTemplate jdbc;

    @Test
    void mapsCreationAndModificationMetadataWithoutLosingTheInstant() {
        var created = OffsetDateTime.parse("2026-01-01T09:00:00+09:00");
        var updated = OffsetDateTime.parse("2026-02-01T12:30:00+09:00");
        jdbc.update("UPDATE auth_user SET created_at=?, updated_at=?, created_by=?, updated_by=? WHERE user_key=?",
                created, updated, "creator", "editor", "rbac-user");
        var user = userDao.findByUserKey("rbac-user");
        assertThat(user.createdAt().toInstant()).isEqualTo(created.toInstant());
        assertThat(user.updatedAt().toInstant()).isEqualTo(updated.toInstant());
        assertThat(user.createdBy()).isEqualTo("creator");
        assertThat(user.updatedBy()).isEqualTo("editor");
        assertThat(user.enabled()).isTrue();
    }

    @BeforeEach
    void createAssignments() {
        jdbc = new JdbcTemplate(appDataSource);
        jdbc.update("INSERT INTO auth_user (user_key, user_id, password_hash) VALUES (?, ?, ?)", "rbac-user", "login-rbac", "test-only");
        jdbc.update("INSERT INTO auth_user (user_key, user_id, password_hash) VALUES (?, ?, ?)", "empty-user", "login-empty", "test-only");
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('READER', 'Reader'), ('EDITOR', 'Editor')");
        jdbc.update("INSERT INTO auth_permission (permission_code, display_name) VALUES (?, 'Read'), (?, 'Delete')", READ, DELETE);
        jdbc.update("INSERT INTO auth_user_role (user_key, role_code) VALUES ('rbac-user', 'READER'), ('rbac-user', 'EDITOR')");
        jdbc.update("INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('READER', ?), ('EDITOR', ?), ('EDITOR', ?)", READ, READ, DELETE);
    }

    @Test
    void combinesMultipleRolesWithoutDuplicatePermissionsOrOtherUsersPermissions() {
        assertThat(permissionDao.findEffectivePermissionCodesByUserKey("rbac-user"))
                .containsExactlyInAnyOrder(READ, DELETE);
        assertThat(permissionDao.findEffectivePermissionCodesByUserKey("empty-user")).isEmpty();
        assertThat(permissionDao.findEffectivePermissionCodesByUserKey("missing-user")).isEmpty();
        assertThat(permissionDao.findEffectivePermissionCodesByUserKey("' OR '1'='1")).isEmpty();
    }

    @Test
    void wiredServiceReflectsAssignmentAndRolePermissionRevocation() {
        authorizationService.requirePermission("rbac-user", DELETE);
        jdbc.update("DELETE FROM auth_user_role WHERE user_key = 'rbac-user' AND role_code = 'EDITOR'");
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
        jdbc.update("UPDATE auth_user SET enabled = FALSE, updated_at = CURRENT_TIMESTAMP, updated_by = '0' WHERE user_key = 'rbac-user'");
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
                "INSERT INTO auth_user_role (user_key, role_code) VALUES ('rbac-user', 'READER')",
                "INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('READER', 'dataset:item:read')"
        }) {
            assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void rejectsAllMissingParentReferences() {
        for (String sql : new String[] {
                "INSERT INTO auth_user_role (user_key, role_code) VALUES ('missing-user', 'READER')",
                "INSERT INTO auth_user_role (user_key, role_code) VALUES ('rbac-user', 'MISSING')",
                "INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('MISSING', 'dataset:item:read')",
                "INSERT INTO auth_role_permission (role_code, permission_code) VALUES ('READER', 'missing:item:read')"
        }) {
            assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void restrictsParentDeletionAndKeyUpdatesUntilAssignmentsAreRemoved() {
        for (String sql : new String[] {
                "DELETE FROM auth_user WHERE user_key = 'rbac-user'",
                "DELETE FROM auth_role WHERE role_code = 'READER'",
                "DELETE FROM auth_permission WHERE permission_code = 'dataset:item:read'",
                "UPDATE auth_user SET user_key = 'renamed' WHERE user_key = 'rbac-user'",
                "UPDATE auth_role SET role_code = 'RENAMED' WHERE role_code = 'READER'",
                "UPDATE auth_permission SET permission_code = 'renamed:item:read' WHERE permission_code = 'dataset:item:read'"
        }) {
            assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
        }
        jdbc.update("DELETE FROM auth_user_role WHERE user_key = 'rbac-user'");
        assertThatCode(() -> jdbc.update("DELETE FROM auth_user WHERE user_key = 'rbac-user'"))
                .doesNotThrowAnyException();
        jdbc.update("DELETE FROM auth_role_permission WHERE role_code = 'READER'");
        assertThatCode(() -> jdbc.update("DELETE FROM auth_role WHERE role_code = 'READER'"))
                .doesNotThrowAnyException();
        jdbc.update("DELETE FROM auth_role_permission WHERE permission_code = ?", READ);
        assertThatCode(() -> jdbc.update("DELETE FROM auth_permission WHERE permission_code = ?", READ))
                .doesNotThrowAnyException();
    }
}
