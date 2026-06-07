package com.xiyu.bid.workflowform.application.port;

import com.xiyu.bid.workflowform.application.command.WorkflowFormOaBindingCommand;
import com.xiyu.bid.workflowform.application.command.WorkflowFormTemplateDraftCommand;

import java.util.List;
import java.util.Optional;

public interface WorkflowFormAdminStore {
    List<WorkflowFormTemplateAdminRecord> listTemplates();

    Optional<WorkflowFormTemplateAdminRecord> findDraft(String templateCode);

    Optional<WorkflowFormTemplateRecord> findActive(String templateCode);

    Optional<OaProcessBindingRecord> findBinding(String templateCode);

    List<WorkflowFormTemplateVersionRecord> listVersions(String templateCode);

    WorkflowFormTemplateAdminRecord saveDraft(WorkflowFormTemplateDraftCommand command);

    OaProcessBindingRecord saveBinding(WorkflowFormOaBindingCommand command);

    WorkflowFormTemplateAdminRecord publish(String templateCode, String publishedBy);

    WorkflowFormTemplateAdminRecord rollback(String templateCode, int targetVersion, String operator);
}
