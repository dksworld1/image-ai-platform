package com.imageaiplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.imageaiplatform.auth.dao.AuthUserDao;
import com.imageaiplatform.auth.dao.UserAccountDao;
import com.imageaiplatform.auth.dao.UserRoleDao;
import com.imageaiplatform.auth.service.AdminBootstrapService;
import com.imageaiplatform.auth.service.AuthService;
import com.imageaiplatform.auth.service.UserAccountService;
import com.imageaiplatform.auth.service.UserRoleService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

@SpringBootTest(properties = {
        "app.datasource.url=jdbc:h2:mem:admin-protection;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "app.admin-bootstrap-only=true"
})
@ActiveProfiles("test")
class AdminProtectionTest {
    private static final String PASSWORD = "test-only-admin-password";
    @Autowired private AdminBootstrapService bootstrap;
    @Autowired private UserRoleService roles;
    @Autowired private UserAccountService accounts;
    @Autowired private AuthService login;
    @Autowired private AuthUserDao users;
    @Autowired private UserAccountDao accountDao;
    @Autowired private UserRoleDao roleDao;
    @Autowired @Qualifier("appDataSource") private DataSource dataSource;
    @Autowired @Qualifier("appTransactionManager") private PlatformTransactionManager transactionManager;
    @Autowired private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM auth_audit_log");
        jdbc.update("DELETE FROM auth_user_role");
        jdbc.update("DELETE FROM auth_user");
    }

    @Test
    void createsUsableAdministratorWithHashedPasswordAndAudit() {
        bootstrap.create("admin-one", PASSWORD);
        assertThat(roleDao.countActiveAdmins()).isEqualTo(1);
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, users.findByUsername("admin-one").passwordHash())).isTrue();
        assertThat(login.login("admin-one", PASSWORD).value()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT actor_username FROM auth_audit_log", String.class)).isEqualTo("BOOTSTRAP");
    }

    @Test
    void neverOverwritesExistingAccountOrResetsAdministrator() {
        accountDao.create("occupied", "existing-hash");
        assertThatThrownBy(() -> bootstrap.create("occupied", PASSWORD)).isInstanceOf(IllegalStateException.class);
        assertThat(users.findByUsername("occupied").passwordHash()).isEqualTo("existing-hash");
        bootstrap.create("admin-one", PASSWORD);
        assertThatThrownBy(() -> bootstrap.create("admin-two", PASSWORD)).isInstanceOf(IllegalStateException.class);
        assertThat(users.findByUsername("admin-two")).isNull();
        jdbc.update("UPDATE auth_user SET enabled = FALSE WHERE username = 'admin-one'");
        assertThatThrownBy(() -> bootstrap.create("admin-two", PASSWORD)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void simultaneousBootstrapCreatesOnlyOneAdministrator() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> createAfter(start, "admin-one"));
            var second = executor.submit(() -> createAfter(start, "admin-two"));
            start.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS) ^ second.get(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(roleDao.countAdminAssignments()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_user", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_audit_log", Integer.class)).isEqualTo(1);
    }

    @Test
    void lastActiveAdminCannotLoseRoleOrBeDisabled() {
        bootstrap.create("admin-one", PASSWORD);
        assertThatThrownBy(() -> roles.revoke("admin-one", "admin-one", "ADMIN")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> accounts.setEnabled("admin-one", "admin-one", false)).isInstanceOf(AccessDeniedException.class);
        assertThat(roleDao.countActiveAdmins()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_audit_log", Integer.class)).isEqualTo(1);
    }

    @Test
    void incompatibleOuterTransactionCannotUseStaleAdministratorCounts() {
        bootstrap.create("admin-one", PASSWORD);
        var outer = new TransactionTemplate(transactionManager);
        outer.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(() -> outer.executeWithoutResult(status -> roles.revoke("admin-one", "admin-one", "ADMIN")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READ_COMMITTED");
        assertThat(roleDao.countActiveAdmins()).isEqualTo(1);
    }

    @Test
    void anotherActiveAdminAllowsRevocation() {
        createTwoAdmins();
        roles.revoke("admin-one", "admin-one", "ADMIN");
        assertThat(roleDao.isActiveAdmin("admin-one")).isFalse();
        assertThat(roleDao.isActiveAdmin("admin-two")).isTrue();
    }

    @Test
    void disabledAdminDoesNotCountAsRemainingAdministrator() {
        createTwoAdmins();
        accounts.setEnabled("admin-one", "admin-two", false);
        assertThat(roleDao.countAdminAssignments()).isEqualTo(2);
        assertThat(roleDao.countActiveAdmins()).isEqualTo(1);
        assertThatThrownBy(() -> roles.revoke("admin-one", "admin-one", "ADMIN")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void concurrentRevocationAndDisablingCannotRemoveBothActiveAdmins() throws Exception {
        createTwoAdmins();
        accountDao.create("operator", "unused-hash");
        jdbc.update("INSERT INTO auth_user_role VALUES ('operator', 'MEMBER')");
        jdbc.update("INSERT INTO auth_role_permission VALUES ('MEMBER', 'auth:user:manage')");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                await(start);
                try { roles.revoke("operator", "admin-one", "ADMIN"); return true; }
                catch (AccessDeniedException exception) { return false; }
            });
            var second = executor.submit(() -> {
                await(start);
                try { accounts.setEnabled("operator", "admin-two", false); return true; }
                catch (AccessDeniedException exception) { return false; }
            });
            start.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS) ^ second.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            jdbc.update("DELETE FROM auth_role_permission WHERE role_code = 'MEMBER'");
        }
        assertThat(roleDao.countActiveAdmins()).isEqualTo(1);
    }

    @Test
    void failedBootstrapAuditRollsBackAccountAndAssignment() {
        var target = new AdminBootstrapService(users, accountDao, roleDao,
                (actor, username, role, action) -> { throw new DataIntegrityViolationException("Audit failed"); });
        var advice = new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource());
        advice.setBeanFactory(context.getBeanFactory());
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(advice);
        var failingBootstrap = (AdminBootstrapService) proxy.getProxy();
        assertThatThrownBy(() -> failingBootstrap.create("admin-one", PASSWORD)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.findByUsername("admin-one")).isNull();
        assertThat(roleDao.countAdminAssignments()).isZero();
        bootstrap.create("admin-two", PASSWORD);
        assertThat(roleDao.countActiveAdmins()).isEqualTo(1);
    }

    private void createTwoAdmins() {
        bootstrap.create("admin-one", PASSWORD);
        accountDao.create("admin-two", "unused-hash");
        roles.grant("admin-one", "admin-two", "ADMIN");
    }

    private boolean createAfter(CountDownLatch start, String username) {
        await(start);
        try { bootstrap.create(username, PASSWORD); return true; }
        catch (IllegalStateException exception) { return false; }
    }

    private static void await(CountDownLatch start) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Start timed out"); }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
