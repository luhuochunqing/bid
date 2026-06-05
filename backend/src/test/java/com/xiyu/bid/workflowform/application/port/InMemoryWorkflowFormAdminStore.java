package com.xiyu.bid.workflowform.application.port;

import com.xiyu.bid.workflowform.application.command.WorkflowFormOaBindingCommand;
import com.xiyu.bid.workflowform.application.command.WorkflowFormTemplateDraftCommand;
import java.time.LocalDateTime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryWorkflowFormAdminStore implements WorkflowFormAdminStore {
    private final Map<String, WorkflowFormTemplateAdminRecord> drafts = new LinkedHashMap<>();
    private final Map<String, WorkflowFormTemplateRecord> active = new LinkedHashMap<>();
    private final Map<String, OaProcessBindingRecord> bindings = new LinkedHashMap<>();
    private final Map<String, Integer> versions = new LinkedHashMap<>();
    private final Map<String, Map<Integer, Map<String, Object>>> versionSchemas = new LinkedHashMap<>();

    @Override
    public List<WorkflowFormTemplateAdminRecord> listTemplates() {
        return drafts.values().stream()
                .map(record -> new WorkflowFormTemplateAdminRecord(record.templateCode(), record.name(),
                        record.businessType(), record.version(), record.enabled(), record.status(), record.schema(),
                        bindings.get(record.templateCode())))
                .toList();
    }

    @Override
    public Optional<WorkflowFormTemplateAdminRecord> findDraft(String templateCode) {
        return Optional.ofNullable(drafts.get(templateCode));
    }

    @Override
    public Optional<WorkflowFormTemplateRecord> findActive(String templateCode) {
        return Optional.ofNullable(active.get(templateCode));
    }

    @Override
    public Optional<OaProcessBindingRecord> findBinding(String templateCode) {
        return Optional.ofNullable(bindings.get(templateCode));
    }

    @Override
    public List<WorkflowFormTemplateVersionRecord> listVersions(String templateCode) {
        Map<Integer, Map<String, Object>> snapshots = versionSchemas.getOrDefault(templateCode, Map.of());
        WorkflowFormTemplateAdminRecord latestDraft = drafts.get(templateCode);
        if (latestDraft == null) {
            return List.of();
        }
        return snapshots.entrySet().stream()
                .sorted((left, right) -> right.getKey().compareTo(left.getKey()))
                .map(entry -> new WorkflowFormTemplateVersionRecord(
                        templateCode,
                        entry.getKey(),
                        latestDraft.name(),
                        latestDraft.businessType(),
                        latestDraft.enabled(),
                        "system",
                        LocalDateTime.now(),
                        entry.getValue()
                ))
                .toList();
    }

    @Override
    public WorkflowFormTemplateAdminRecord saveDraft(WorkflowFormTemplateDraftCommand command) {
        WorkflowFormTemplateAdminRecord record = new WorkflowFormTemplateAdminRecord(command.templateCode(),
                command.name(), command.businessType(), versions.getOrDefault(command.templateCode(), 0),
                command.enabled(), "DRAFT", command.schema(), bindings.get(command.templateCode()));
        drafts.put(command.templateCode(), record);
        return record;
    }

    @Override
    public OaProcessBindingRecord saveBinding(WorkflowFormOaBindingCommand command) {
        OaProcessBindingRecord record = new OaProcessBindingRecord(command.templateCode(), command.provider(),
                command.workflowCode(), command.fieldMapping(), command.enabled());
        bindings.put(command.templateCode(), record);
        return record;
    }

    @Override
    public WorkflowFormTemplateAdminRecord publish(String templateCode, String publishedBy) {
        WorkflowFormTemplateAdminRecord draft = drafts.get(templateCode);
        int next = versions.getOrDefault(templateCode, 0) + 1;
        versions.put(templateCode, next);
        active.put(templateCode, new WorkflowFormTemplateRecord(templateCode, draft.businessType(), next, draft.schema()));
        versionSchemas.computeIfAbsent(templateCode, key -> new LinkedHashMap<>()).put(next, draft.schema());
        WorkflowFormTemplateAdminRecord published = new WorkflowFormTemplateAdminRecord(templateCode, draft.name(),
                draft.businessType(), next, draft.enabled(), "PUBLISHED", draft.schema(), bindings.get(templateCode));
        drafts.put(templateCode, published);
        return published;
    }

    @Override
    public WorkflowFormTemplateAdminRecord rollback(String templateCode, int targetVersion, String operator) {
        WorkflowFormTemplateAdminRecord draft = drafts.get(templateCode);
        if (draft == null) {
            throw new IllegalArgumentException("流程表单草稿不存在");
        }
        Map<String, Object> snapshot = versionSchemas.getOrDefault(templateCode, Map.of())
                .get(targetVersion);
        if (snapshot == null) {
            throw new IllegalArgumentException("未找到目标历史版本: " + targetVersion);
        }
        drafts.put(templateCode, new WorkflowFormTemplateAdminRecord(
                templateCode,
                draft.name(),
                draft.businessType(),
                versions.getOrDefault(templateCode, 0),
                draft.enabled(),
                "DRAFT",
                snapshot,
                bindings.get(templateCode)
        ));
        return publish(templateCode, operator);
    }
}
