package com.imageaiplatform.auth.service;

import com.imageaiplatform.auth.dao.AuthAuditDao;
import com.imageaiplatform.auth.dao.AuthUserDao;
import com.imageaiplatform.auth.dao.UserAccountDao;
import com.imageaiplatform.auth.dao.UserRoleDao;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** 명시적인 최초 관리자 생성 모드에서만 사용합니다. */
@Service
@ConditionalOnProperty(name = "app.admin-bootstrap-only", havingValue = "true")
public class AdminBootstrapService {
    private final AuthUserDao users;
    private final UserAccountDao accounts;
    private final UserRoleDao roles;
    private final AuthAuditDao audit;

    public AdminBootstrapService(AuthUserDao users, UserAccountDao accounts, UserRoleDao roles, AuthAuditDao audit) {
        this.users = users;
        this.accounts = accounts;
        this.roles = roles;
        this.audit = audit;
    }

    @Transactional(transactionManager = "appTransactionManager", isolation = Isolation.READ_COMMITTED)
    public void create(String username, String password) {
        if (username == null || username.isBlank() || username.length() > 100
                || password == null || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A valid initial administrator username and password are required.");
        }
        roles.lockAdminRole();
        if (roles.countAdminAssignments() > 0 || users.findByUsername(username) != null) {
            throw new IllegalStateException("Administrator already provisioned or username already exists; no changes made.");
        }
        accounts.create(username, new BCryptPasswordEncoder().encode(password));
        roles.grant(username, "ADMIN");
        audit.recordRoleChange("BOOTSTRAP", username, "ADMIN", "ROLE_GRANTED");
    }
}
