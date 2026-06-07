package com.xiyu.bid.workflowform.application.service;

class NoopWorkflowFormAccessGuard implements WorkflowFormAccessGuard {
    @Override
    public void assertCanAccessProject(Long projectId) {
    }
}
