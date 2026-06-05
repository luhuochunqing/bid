package com.xiyu.bid.workflowform.infrastructure.qualification;

import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.workflowform.application.service.WorkflowFormAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectAccessWorkflowFormGuard implements WorkflowFormAccessGuard {

    private final ProjectAccessScopeService projectAccessScopeService;

    @Override
    public void assertCanAccessProject(Long projectId) {
        if (projectId != null) {
            projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
        }
    }
}
