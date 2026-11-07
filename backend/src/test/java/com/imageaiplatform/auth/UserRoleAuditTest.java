package com.imageaiplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.imageaiplatform.auth.dao.UserRoleDao;
import com.imageaiplatform.auth.service.AuthorizationService;
import com.imageaiplatform.auth.service.UserRoleService;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

@SpringBootTest(properties = "app.datasource.url=jdbc:h2:mem:user-role-audit;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class UserRoleAuditTest {
    @Autowired @Qualifier("appDataSource") private DataSource dataSource;
    @Autowired private UserRoleService service;
    @Autowired private AuthorizationService authorizationService;
    @Autowired private UserRoleDao userRoleDao;
    @Autowired @Qualifier("appTransactionManager") private PlatformTransactionManager transactionManager;
    @Autowired private ConfigurableApplicationContext context;

    private JdbcTemplate jdbc;
    private String actor;
    private String target;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(dataSource);
        actor = "manager-" + UUID.randomUUID();
        target = "target-" + UUID.randomUUID();
        jdbc.update("INSERT INTO auth_user (username, password_hash) VALUES (?, 'unused'), (?, 'unused')", actor, target);
        // 권한 부여는 테스트 fixture일 뿐 V3의 ADMIN 기본 권한이 아닙니다.
        jdbc.update("MERGE INTO auth_permission (permission_code, display_name) KEY(permission_code) VALUES ('auth:user:manage', 'Manage users')");
        jdbc.update("MERGE INTO auth_role_permission (role_code, permission_code) KEY(role_code, permission_code) VALUES ('ADMIN', 'auth:user:manage')");
        jdbc.update("INSERT INTO auth_user_role (username, role_code) VALUES (?, 'ADMIN')", actor);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM auth_user_role WHERE username IN (?, ?)", actor, target);
        jdbc.update("DELETE FROM auth_user WHERE username IN (?, ?)", actor, target);
    }

    @Test
    void grantsAndRevokesRoleWithCommittedAuditDetails() {
        service.grant(actor, target, "MEMBER");
        assertThat(assignmentCount()).isEqualTo(1);
        service.revoke(actor, target, "MEMBER");
        assertThat(assignmentCount()).isZero();
        var entries = jdbc.queryForList("SELECT * FROM auth_audit_log WHERE target_username = ? ORDER BY id", target);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0)).containsEntry("ACTOR_USERNAME", actor).containsEntry("TARGET_USERNAME", target)
                .containsEntry("ROLE_CODE", "MEMBER").containsEntry("ACTION", "ROLE_GRANTED");
        assertThat(entries.get(0).get("OCCURRED_AT")).isNotNull();
        assertThat(entries.get(1)).containsEntry("ACTION", "ROLE_REVOKED");
    }

    @Test
    void repeatedRequestsDoNotCreateFalseChangeEvents() {
        service.grant(actor, target, "MEMBER");
        service.grant(actor, target, "MEMBER");
        service.revoke(actor, target, "MEMBER");
        service.revoke(actor, target, "MEMBER");
        assertThat(auditCount()).isEqualTo(2);
    }

    @Test
    void unauthorizedAndDisabledActorsCannotChangeRolesOrCreateSuccessEvents() {
        jdbc.update("DELETE FROM auth_user_role WHERE username = ?", actor);
        assertThatThrownBy(() -> service.grant(actor, target, "MEMBER")).isInstanceOf(AccessDeniedException.class);
        jdbc.update("UPDATE auth_user SET enabled = FALSE WHERE username = ?", actor);
        assertThatThrownBy(() -> service.grant(actor, target, "MEMBER")).isInstanceOf(BadCredentialsException.class);
        assertThat(assignmentCount()).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test
    void auditFailureRollsBackGrant() {
        var failingService = serviceWithFailedAudit();
        assertThatThrownBy(() -> failingService.grant(actor, target, "MEMBER")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(assignmentCount()).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test
    void auditFailureRollsBackRevocation() {
        service.grant(actor, target, "MEMBER");
        var failingService = serviceWithFailedAudit();
        assertThatThrownBy(() -> failingService.revoke(actor, target, "MEMBER")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(assignmentCount()).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    void unknownUserOrRoleDoesNotProduceSuccessAudit() {
        assertThatThrownBy(() -> service.grant(actor, target, "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> service.grant(actor, "missing-user", "MEMBER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(assignmentCount()).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test
    void deletingAccountDoesNotDeleteAuditHistory() {
        service.grant(actor, target, "MEMBER");
        service.revoke(actor, target, "MEMBER");
        jdbc.update("DELETE FROM auth_user WHERE username = ?", target);
        assertThat(auditCount()).isEqualTo(2);
    }

    @Test
    void invalidTargetInputsAreRejectedWithoutChanges() {
        assertThatThrownBy(() -> service.grant(actor, null, "MEMBER")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.grant(actor, target, "member")).isInstanceOf(IllegalArgumentException.class);
        assertThat(assignmentCount()).isZero();
        assertThat(auditCount()).isZero();
    }

    private UserRoleService serviceWithFailedAudit() {
        var targetService = new UserRoleService(authorizationService, userRoleDao,
                (actorName, targetName, role, action) -> { throw new DataIntegrityViolationException("Audit storage failure"); });
        var transactionAdvice = new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource());
        transactionAdvice.setBeanFactory(context.getBeanFactory());
        var proxy = new ProxyFactory(targetService);
        proxy.addAdvice(transactionAdvice);
        return (UserRoleService) proxy.getProxy();
    }

    private Integer assignmentCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM auth_user_role WHERE username = ?", Integer.class, target);
    }

    private Integer auditCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM auth_audit_log WHERE target_username = ?", Integer.class, target);
    }
}
