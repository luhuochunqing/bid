package com.xiyu.bid.workflowform.application.service;

import com.xiyu.bid.workflowform.application.command.WorkflowFormOaBindingCommand;
import com.xiyu.bid.workflowform.application.command.WorkflowFormTemplateDraftCommand;
import com.xiyu.bid.workflowform.application.port.InMemoryWorkflowFormAdminStore;
import com.xiyu.bid.workflowform.application.port.OaStartCommand;
import com.xiyu.bid.workflowform.application.port.OaStartResult;
import com.xiyu.bid.workflowform.application.port.OaWorkflowGateway;
import com.xiyu.bid.workflowform.domain.FormBusinessType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowFormAdminServiceTest {

    @Test
    void admin_can_save_draft_bind_oa_and_publish_version() {
        InMemoryWorkflowFormAdminStore store = new InMemoryWorkflowFormAdminStore();
        WorkflowFormAdminService service = new WorkflowFormAdminService(store, new CapturingOaWorkflowGateway());

        var draft = service.saveDraft(new WorkflowFormTemplateDraftCommand(
                "SEAL_APPLY",
                "用章申请",
                FormBusinessType.GENERAL_WORKFLOW,
                true,
                schema("title")
        ));
        service.saveOaBinding(new WorkflowFormOaBindingCommand(
                "SEAL_APPLY",
                "WEAVER",
                "WF_SEAL",
                mapping("title", "field_title"),
                true
        ));
        var published = service.publish("SEAL_APPLY", "admin");

        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(published.version()).isEqualTo(1);
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(store.findActive("SEAL_APPLY").orElseThrow().schema()).isEqualTo(schema("title"));
    }

    @Test
    void preview_trial_submit_returns_mapped_oa_payload_without_publishing_side_effects() {
        InMemoryWorkflowFormAdminStore store = new InMemoryWorkflowFormAdminStore();
        CapturingOaWorkflowGateway gateway = new CapturingOaWorkflowGateway();
        WorkflowFormAdminService service = new WorkflowFormAdminService(store, gateway);

        service.saveDraft(new WorkflowFormTemplateDraftCommand(
                "SEAL_APPLY",
                "用章申请",
                FormBusinessType.GENERAL_WORKFLOW,
                true,
                schema("title")
        ));
        service.saveOaBinding(new WorkflowFormOaBindingCommand(
                "SEAL_APPLY",
                "WEAVER",
                "WF_SEAL",
                mapping("title", "field_title"),
                true
        ));

        var preview = service.previewTrialSubmit(
                "SEAL_APPLY",
                Map.of("title", "测试申请"),
                "李总"
        );

        assertThat(preview.oaStarted()).isTrue();
        assertThat(preview.oaInstanceId()).isEqualTo("OA-TRIAL");
        assertThat(preview.payload()).containsEntry("workflowCode", "WF_SEAL");
        assertThat(preview.payload()).containsEntry("trial", true);
        assertThat(gateway.lastCommand.trial()).isTrue();
        assertThat((Map<String, Object>) preview.payload().get("mainFields")).containsEntry("field_title", "测试申请");
        assertThat(store.findActive("SEAL_APPLY")).isEmpty();
    }

    @Test
    void list_templates_returns_oa_binding_for_editing_existing_mapping() {
        InMemoryWorkflowFormAdminStore store = new InMemoryWorkflowFormAdminStore();
        WorkflowFormAdminService service = new WorkflowFormAdminService(store, new CapturingOaWorkflowGateway());

        service.saveDraft(new WorkflowFormTemplateDraftCommand(
                "SEAL_APPLY", "用章申请", FormBusinessType.GENERAL_WORKFLOW, true, schema("title")));
        service.saveOaBinding(new WorkflowFormOaBindingCommand(
                "SEAL_APPLY", "WEAVER", "WF_SEAL", mapping("title", "oa_title"), true));

        assertThat(service.listTemplates().getFirst().oaBinding()).isNotNull();
        assertThat(service.listTemplates().getFirst().oaBinding().workflowCode()).isEqualTo("WF_SEAL");
        assertThat(service.listTemplates().getFirst().oaBinding().fieldMapping()).isEqualTo(mapping("title", "oa_title"));
    }

    @Test
    void rollback_to_historical_version_creates_new_version_record() {
        InMemoryWorkflowFormAdminStore store = new InMemoryWorkflowFormAdminStore();
        WorkflowFormAdminService service = new WorkflowFormAdminService(store, new CapturingOaWorkflowGateway());

        service.saveDraft(new WorkflowFormTemplateDraftCommand(
                "SEAL_APPLY", "用章申请", FormBusinessType.GENERAL_WORKFLOW, true, schema("title")));
        service.saveOaBinding(new WorkflowFormOaBindingCommand(
                "SEAL_APPLY", "WEAVER", "WF_SEAL", mapping("title", "field_title"), true));
        service.publish("SEAL_APPLY", "admin");
        service.saveDraft(new WorkflowFormTemplateDraftCommand(
                "SEAL_APPLY", "用章申请", FormBusinessType.GENERAL_WORKFLOW, true, schema("title", "第二版")));

        var rolled = service.rollback("SEAL_APPLY", 1, "admin");

        assertThat(rolled.version()).isEqualTo(2);
        assertThat(rolled.schema()).isEqualTo(schema("title"));
    }

    private static Map<String, Object> schema(String key) {
        return Map.of("fields", List.of(Map.of("key", key, "label", "标题", "type", "text", "required", true)));
    }

    private static Map<String, Object> schema(String key, String label) {
        return Map.of("fields", List.of(Map.of("key", key, "label", label, "type", "text", "required", true)));
    }

    private static Map<String, Object> mapping(String sourceKey, String target) {
        return Map.of(
                "workflowCode", "WF_SEAL",
                "mainFields", List.of(Map.of("source", "formData." + sourceKey, "target", target, "type", "string", "required", true))
        );
    }

    static class CapturingOaWorkflowGateway implements OaWorkflowGateway {
        OaStartCommand lastCommand;

        @Override
        public OaStartResult startProcess(OaStartCommand command) {
            lastCommand = command;
            return new OaStartResult(true, "OA-TRIAL", null);
        }
    }
}
