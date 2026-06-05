package com.xiyu.bid.workflowform.controller;

import com.xiyu.bid.workflowform.application.WorkflowFormConfigException;
import com.xiyu.bid.workflowform.application.service.WorkflowFormAdminService;
import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateVersionRecord;
import com.xiyu.bid.workflowform.domain.FormBusinessType;
import com.xiyu.bid.workflowform.application.view.WorkflowFormTrialSubmitView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowFormAdminControllerTest {

    private WorkflowFormAdminService adminService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminService = mock(WorkflowFormAdminService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkflowFormAdminController(adminService)).build();
    }

    @Test
    void trial_submit_returns_oa_test_result_and_payload() throws Exception {
        when(adminService.previewTrialSubmit(eq("SEAL_APPLY"), any(), eq("测试管理员")))
                .thenReturn(new WorkflowFormTrialSubmitView(
                        true,
                        "MOCK-TRIAL-OA-1",
                        null,
                        Map.of("trial", true, "workflowCode", "WF_SEAL")
                ));

        mockMvc.perform(post("/api/admin/workflow-forms/templates/SEAL_APPLY/oa/test-submit")
                        .contentType("application/json")
                        .content("""
                                {
                                  "applicantName": "测试管理员",
                                  "formData": { "title": "测试" }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oaStarted").value(true))
                .andExpect(jsonPath("$.data.oaInstanceId").value("MOCK-TRIAL-OA-1"))
                .andExpect(jsonPath("$.data.payload.trial").value(true));
    }

    @Test
    void config_exception_returns_uniform_error_code() throws Exception {
        when(adminService.previewTrialSubmit(eq("SEAL_APPLY"), any(), eq("测试管理员")))
                .thenThrow(new WorkflowFormConfigException("流程表单未配置 OA 绑定"));

        mockMvc.perform(post("/api/admin/workflow-forms/templates/SEAL_APPLY/oa/test-submit")
                        .contentType("application/json")
                        .content("""
                                {
                                  "applicantName": "测试管理员",
                                  "formData": { "title": "测试" }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.msg").value("流程表单未配置 OA 绑定"))
                .andExpect(jsonPath("$.data.errorCode").value(WorkflowFormConfigException.ERROR_CODE));
    }

    @Test
    void versions_endpoint_returns_version_list() throws Exception {
        when(adminService.listVersions("SEAL_APPLY"))
                .thenReturn(List.of(
                        new WorkflowFormTemplateVersionRecord(
                                "SEAL_APPLY",
                                2,
                                "用章申请",
                                FormBusinessType.GENERAL_WORKFLOW,
                                true,
                                "admin",
                                null,
                                Map.of("fields", List.of(Map.of("key", "title")))
                        ),
                        new WorkflowFormTemplateVersionRecord(
                                "SEAL_APPLY",
                                1,
                                "用章申请",
                                FormBusinessType.GENERAL_WORKFLOW,
                                true,
                                "admin",
                                null,
                                Map.of("fields", List.of(Map.of("key", "title")))
                        )
                ));

        mockMvc.perform(get("/api/admin/workflow-forms/templates/SEAL_APPLY/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[1].version").value(1));
    }

    @Test
    void rollback_version_endpoint_triggers_admin_service() throws Exception {
        when(adminService.rollback(eq("SEAL_APPLY"), eq(1), eq("system")))
                .thenReturn(null);

        mockMvc.perform(post("/api/admin/workflow-forms/templates/SEAL_APPLY/versions/1/rollback"))
                .andExpect(status().isOk());

        verify(adminService).rollback("SEAL_APPLY", 1, "system");
    }
}
