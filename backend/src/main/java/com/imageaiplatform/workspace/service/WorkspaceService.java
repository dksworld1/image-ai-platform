package com.imageaiplatform.workspace.service;

import com.imageaiplatform.workspace.dao.WorkspaceDao;
import com.imageaiplatform.workspace.domain.WorkspaceSummary;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {
    private final WorkspaceDao workspaceDao;

    public WorkspaceService(WorkspaceDao workspaceDao) {
        this.workspaceDao = workspaceDao;
    }

    @PreAuthorize("@authorizationService.isActiveAccount()")
    @Transactional(value = "appTransactionManager", readOnly = true)
    public List<WorkspaceSummary> list() {
        return workspaceDao.findActiveByUserKey(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public boolean isMemberOfAll(List<String> workspaceIds) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() && workspaceIds != null && !workspaceIds.isEmpty()
                && workspaceDao.findActiveByUserKey(authentication.getName()).stream()
                    .map(WorkspaceSummary::workspaceId).toList().containsAll(workspaceIds);
    }
}
