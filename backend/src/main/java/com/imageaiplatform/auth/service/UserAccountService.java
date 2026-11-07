package com.imageaiplatform.auth.service;

import com.imageaiplatform.auth.dao.UserAccountDao;
import com.imageaiplatform.auth.dao.UserRoleDao;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@ConditionalOnProperty(name = "app.database-migration-only", havingValue = "false", matchIfMissing = true)
public class UserAccountService {
    private final AuthorizationService authorizationService;
    private final UserRoleDao userRoleDao;
    private final UserAccountDao userAccountDao;

    public UserAccountService(AuthorizationService authorizationService, UserRoleDao userRoleDao, UserAccountDao userAccountDao) {
        this.authorizationService = authorizationService;
        this.userRoleDao = userRoleDao;
        this.userAccountDao = userAccountDao;
    }

    @Transactional(transactionManager = "appTransactionManager", isolation = Isolation.READ_COMMITTED)
    public void setEnabled(String actorUsername, String targetUsername, Boolean enabled) {
        if (targetUsername == null || targetUsername.isBlank() || targetUsername.length() > 100 || enabled == null) {
            throw new IllegalArgumentException("A target username and enabled state are required.");
        }
        userRoleDao.lockAdminRole();
        authorizationService.requirePermission(actorUsername, "auth:user:manage");
        if (Boolean.FALSE.equals(enabled) && userRoleDao.isActiveAdmin(targetUsername) && userRoleDao.countActiveAdmins() <= 1) {
            throw new AccessDeniedException("Cannot disable the last active administrator.");
        }
        if (userAccountDao.setEnabled(targetUsername, enabled) != 1) {
            throw new IllegalArgumentException("Target account does not exist.");
        }
    }
}
