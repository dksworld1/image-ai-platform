package com.imageaiplatform.auth.service;

import com.imageaiplatform.auth.dao.AuthAuditDao;
import com.imageaiplatform.auth.dao.UserRoleDao;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.security.access.AccessDeniedException;

/** 수행자 이름은 서버에서 검증한 인증 정보에서 가져와야 합니다. */
@Service
@ConditionalOnProperty(name = "app.database-migration-only", havingValue = "false", matchIfMissing = true)
@Transactional(transactionManager = "appTransactionManager", isolation = Isolation.READ_COMMITTED)
public class UserRoleService {
    private final AuthorizationService authorizationService;
    private final UserRoleDao userRoleDao;
    private final AuthAuditDao auditDao;

    public UserRoleService(AuthorizationService authorizationService, UserRoleDao userRoleDao, AuthAuditDao auditDao) {
        this.authorizationService = authorizationService;
        this.userRoleDao = userRoleDao;
        this.auditDao = auditDao;
    }

    public void grant(String actorUsername, String targetUsername, String roleCode) {
        validate(targetUsername, roleCode);
        if ("ADMIN".equals(roleCode)) {
            userRoleDao.lockAdminRole();
        }
        authorizationService.requirePermission(actorUsername, "auth:user:manage");
        if (userRoleDao.grant(targetUsername, roleCode) > 0) {
            auditDao.recordRoleChange(actorUsername, targetUsername, roleCode, "ROLE_GRANTED");
        }
    }

    public void revoke(String actorUsername, String targetUsername, String roleCode) {
        validate(targetUsername, roleCode);
        if ("ADMIN".equals(roleCode)) {
            userRoleDao.lockAdminRole();
        }
        authorizationService.requirePermission(actorUsername, "auth:user:manage");
        if ("ADMIN".equals(roleCode) && userRoleDao.isActiveAdmin(targetUsername)
                && userRoleDao.countActiveAdmins() <= 1) {
            throw new AccessDeniedException("Cannot revoke the last active administrator.");
        }
        if (userRoleDao.revoke(targetUsername, roleCode) > 0) {
            auditDao.recordRoleChange(actorUsername, targetUsername, roleCode, "ROLE_REVOKED");
        }
    }

    private void validate(String username, String roleCode) {
        if (username == null || username.isBlank() || username.length() > 100
                || roleCode == null || roleCode.length() > 100 || !roleCode.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("A valid target username and role code are required.");
        }
    }
}
