package com.xiyu.bid.workflowform.application.service;

/** ProjectAccessScopeService-backed accessGuard boundary for workflow form project records. */
public interface WorkflowFormAccessGuard {
    void assertCanAccessProject(Long projectId);
}
