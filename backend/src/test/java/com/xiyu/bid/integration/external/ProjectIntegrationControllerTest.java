// Input: ProjectIntegrationController（构造注入：命令服务 + ApiKeyUserResolver）+ GlobalExceptionHandler
// Output: MockMvc 测试 — PUT /api/integration/projects/{sourceSystem}/{tenderId} 契约
// Pos: Test/合约验证 — 接口六 §3.6（tenderId 定位修正版：200/404/400/skip200 + 标讯不被修改）
package com.xiyu.bid.integration.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.exception.GlobalExceptionHandler;
import com.xiyu.bid.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ProjectIntegrationController} 的 MockMvc 测试（tenderId 定位修正版）。
 *
 * <p>验证接口六契约：
 * <ul>
 *   <li>200：返参结构（success/code/msg/data + applied/projectId/projectName/updatedAt）</li>
 *   <li>200 跳过：标讯未立项时 data 仅含 applied=false</li>
 *   <li>404：标讯不存在走全局异常处理返回标准错误格式</li>
 *   <li>400：路径 tenderId 非数字（如误传 CRM 商机编号 CC...，类型绑定即拒绝）/ 非法请求体</li>
 *   <li>控制器只委派项目服务，不触碰任何标讯服务（标讯不被修改）</li>
 * </ul>
 *
 * <p>注意：standaloneSetup 模式不启用 Spring Security，
 * @PreAuthorize 注解不生效，安全性由 ApiKeyAuthenticationFilterTest 覆盖。
 */
@ExtendWith(MockitoExtension.class)
class ProjectIntegrationControllerTest {

    private static final Long TENDER_ID = 928L;

    @Mock
    private ProjectIntegrationCommandService projectIntegrationCommandService;

    @Mock
    private ApiKeyUserResolver apiKeyUserResolver;

    @InjectMocks
    private ProjectIntegrationController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("PUT /api/integration/projects/CRM/{tenderId} 返回 200 + 标准响应结构（applied=true）")
    void updateProject_shouldReturn200WithContractShape() throws Exception {
        when(projectIntegrationCommandService.updateByTenderId(
                eq("CRM"), eq(TENDER_ID), any(ProjectUpdateRequest.class), any()))
                .thenReturn(ProjectUpdateResponse.builder()
                        .projectId(1L)
                        .projectName("某单位采购项目")
                        .updatedAt("2026-09-01 10:30")
                        .applied(true)
                        .build());

        ProjectUpdateRequest request = ProjectUpdateRequest.builder()
                .projectName("某单位采购项目")
                .evaluationCustomerInfos(List.of(Map.of(
                        "NAME", "张三",
                        "CONTACT", "13800138000",
                        "TEL", "021-12345678",
                        "CONTACTED", "是",
                        "TENDENCY", "支持")))
                .build();

        mockMvc.perform(put("/api/integration/projects/CRM/" + TENDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applied").value(true))
                .andExpect(jsonPath("$.data.projectId").value(1))
                .andExpect(jsonPath("$.data.projectName").value("某单位采购项目"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-09-01 10:30"));
    }

    @Test
    @DisplayName("请求体按契约字段反序列化并透传给服务层（含 TEL 与 evaluationBasic）")
    void updateProject_requestBodyDeserializedToService() throws Exception {
        when(projectIntegrationCommandService.updateByTenderId(
                any(), any(), any(ProjectUpdateRequest.class), any()))
                .thenReturn(ProjectUpdateResponse.builder()
                        .projectId(1L).projectName("P").updatedAt("2026-09-01 10:30").applied(true).build());

        String json = """
                {
                  "projectName": "某单位采购项目",
                  "evaluationBasic": {
                    "mroOfficeFlowAmount": 1200.5,
                    "customerRevenue": 88,
                    "riskAssessment": "风险预判",
                    "contingencyPlan": "兜底方案",
                    "processKnowledge": "流程概述",
                    "supportNotes": "支持备注",
                    "projectPlanGapFiles": [
                      { "fileName": "方案.pdf", "fileUrl": "https://crm.example.com/a.pdf" }
                    ]
                  },
                  "evaluationCustomerInfos": [
                    { "NAME": "张三", "TEL": "021-12345678", "CONTACTED": "是" }
                  ]
                }
                """;

        mockMvc.perform(put("/api/integration/projects/CRM/" + TENDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        ArgumentCaptor<ProjectUpdateRequest> captor = ArgumentCaptor.forClass(ProjectUpdateRequest.class);
        verify(projectIntegrationCommandService)
                .updateByTenderId(eq("CRM"), eq(TENDER_ID), captor.capture(), any());
        ProjectUpdateRequest received = captor.getValue();
        assertThat(received.getProjectName()).isEqualTo("某单位采购项目");
        assertThat(received.getEvaluationBasic().mroOfficeFlowAmount())
                .isEqualByComparingTo(new BigDecimal("1200.5"));
        assertThat(received.getEvaluationBasic().projectPlanGapFiles()).hasSize(1);
        assertThat(received.getEvaluationCustomerInfos().get(0).get("TEL")).isEqualTo("021-12345678");
    }

    @Test
    @DisplayName("标讯存在但未立项 → 200 且 data 仅含 applied=false（跳过语义，不重试不误报）")
    void updateProject_noProjectLinked_shouldReturn200Skip() throws Exception {
        when(projectIntegrationCommandService.updateByTenderId(
                eq("CRM"), eq(TENDER_ID), any(ProjectUpdateRequest.class), any()))
                .thenReturn(ProjectUpdateResponse.builder().applied(false).build());

        mockMvc.perform(put("/api/integration/projects/CRM/" + TENDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"投标中编辑\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applied").value(false))
                .andExpect(jsonPath("$.data.projectId").doesNotExist())
                .andExpect(jsonPath("$.data.projectName").doesNotExist());
    }

    @Test
    @DisplayName("标讯不存在返回 404 + 标准错误格式")
    void updateProject_notFound_shouldReturn404() throws Exception {
        when(projectIntegrationCommandService.updateByTenderId(
                eq("CRM"), eq(TENDER_ID), any(ProjectUpdateRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("标讯不存在: id=" + TENDER_ID));

        mockMvc.perform(put("/api/integration/projects/CRM/" + TENDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("路径 tenderId 非数字（误传商机编号 CC...）→ 类型绑定阶段直接 400")
    void updateProject_nonNumericTenderId_shouldReturn400() throws Exception {
        mockMvc.perform(put("/api/integration/projects/CRM/CC20260827846")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"P\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400));

        verify(projectIntegrationCommandService, never())
                .updateByTenderId(any(), any(), any(), any());
    }

    @Test
    @DisplayName("非法请求体（JSON 解析失败）返回 400")
    void updateProject_malformedBody_shouldReturn400() throws Exception {
        mockMvc.perform(put("/api/integration/projects/CRM/" + TENDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(projectIntegrationCommandService, never())
                .updateByTenderId(any(), any(), any(), any());
    }

    @Test
    @DisplayName("控制器仅委派项目命令服务一次，不触碰任何标讯相关服务（标讯不被修改）")
    void updateProject_onlyDelegatesToProjectService() throws Exception {
        when(projectIntegrationCommandService.updateByTenderId(
                any(), any(), any(ProjectUpdateRequest.class), any()))
                .thenReturn(ProjectUpdateResponse.builder()
                        .projectId(1L).projectName("P").updatedAt("2026-09-01 10:30").applied(true).build());

        mockMvc.perform(put("/api/integration/projects/CRM/" + TENDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"P\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(projectIntegrationCommandService)
                .updateByTenderId(eq("CRM"), eq(TENDER_ID), any(ProjectUpdateRequest.class), any());
        verifyNoMoreInteractions(projectIntegrationCommandService);
        verify(apiKeyUserResolver).resolveUserId();
    }
}
