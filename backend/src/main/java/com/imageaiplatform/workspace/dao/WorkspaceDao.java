package com.imageaiplatform.workspace.dao;

import com.imageaiplatform.workspace.domain.WorkspaceSummary;
import java.util.List;

public interface WorkspaceDao {
    List<WorkspaceSummary> findActiveByUserKey(String userKey);
}
