package com.imageaiplatform.workspace.controller;

import com.imageaiplatform.workspace.domain.WorkspaceSummary;
import com.imageaiplatform.workspace.service.WorkspaceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public List<WorkspaceSummary> list() {
        return workspaceService.list();
    }
}
