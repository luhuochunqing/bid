package com.xiyu.bid.workflowform.application.port;

import java.util.Optional;

public interface WorkflowFormTemplateStore {
    Optional<WorkflowFormTemplateRecord> findActiveByCode(String templateCode);
}
