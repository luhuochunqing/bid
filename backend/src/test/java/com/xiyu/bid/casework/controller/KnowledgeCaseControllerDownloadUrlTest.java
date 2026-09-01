package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.application.CaseExportExcelAppService;
import com.xiyu.bid.casework.application.CasePrecipitationAppService;
import com.xiyu.bid.casework.application.service.CaseDownloadUrlAppService;
import com.xiyu.bid.casework.application.service.CaseExportAppService;
import com.xiyu.bid.casework.application.service.CaseReferenceAppService;
import com.xiyu.bid.casework.application.service.KnowledgeCaseCommandAppService;
import com.xiyu.bid.casework.application.service.KnowledgeCaseQueryAppService;
import com.xiyu.bid.casework.application.service.KnowledgeCaseRecommendAppService;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KnowledgeCaseController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        com.xiyu.bid.config.SecurityConfig.class,
                        com.xiyu.bid.auth.JwtAuthenticationFilter.class,
                        com.xiyu.bid.config.RateLimitFilter.class,
                        com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter.class
                }
        ))
@AutoConfigureMockMvc(addFilters = false)
class KnowledgeCaseControllerDownloadUrlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private KnowledgeCaseQueryAppService queryAppService;
    @MockBean private KnowledgeCaseCommandAppService commandAppService;
    @MockBean private KnowledgeCaseRecommendAppService recommendAppService;
    @MockBean private CaseReferenceAppService caseReferenceAppService;
    @MockBean private CasePrecipitationAppService precipitationAppService;
    @MockBean private CaseExportAppService caseExportZipAppService;
    @MockBean private CaseExportExcelAppService caseExportExcelAppService;
    @MockBean private ProjectAccessScopeService projectAccessScopeService;
    @MockBean private CaseDownloadUrlAppService caseDownloadUrlAppService;

    // TraceFilter 依赖链（与 KnowledgeCaseControllerExportTest 同款处理）
    @MockBean private CurrentUserResolver currentUserResolver;
    @MockBean private com.xiyu.bid.security.EffectiveRoleResolver effectiveRoleResolver;

    @Test
    @WithMockUser(authorities = "project")
    void downloadUrl_returnsSignedPayload() throws Exception {
        when(caseDownloadUrlAppService.signDownloadUrl(1L)).thenReturn(
                new CaseDownloadUrlAppService.DownloadUrlResult(
                        "https://ehsy-widbid.obs.cn-east-3.myhuaweicloud.com/x?Signature=s",
                        "项目复盘-中广核.docx", 300));

        mockMvc.perform(get("/api/cases/1/download-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://ehsy-widbid.obs.cn-east-3.myhuaweicloud.com/x?Signature=s"))
                .andExpect(jsonPath("$.filename").value("项目复盘-中广核.docx"))
                .andExpect(jsonPath("$.expireSeconds").value(300));
    }

    @Test
    @WithMockUser(authorities = "project")
    void downloadUrl_caseNotFound_returns404() throws Exception {
        when(caseDownloadUrlAppService.signDownloadUrl(404L))
                .thenThrow(new BusinessException(404, "案例不存在"));

        mockMvc.perform(get("/api/cases/404/download-url"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "project")
    void downloadUrl_noOriginal_returns409WithMessage() throws Exception {
        when(caseDownloadUrlAppService.signDownloadUrl(2L))
                .thenThrow(new BusinessException(409, "该案例暂无原版标书"));

        mockMvc.perform(get("/api/cases/2/download-url"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg").value("该案例暂无原版标书"));
    }

    @Test
    @WithMockUser(authorities = "project")
    void downloadUrl_signFailed_returns502() throws Exception {
        when(caseDownloadUrlAppService.signDownloadUrl(3L))
                .thenThrow(new BusinessException(502, "下载链接生成失败，请稍后重试"));

        mockMvc.perform(get("/api/cases/3/download-url"))
                .andExpect(status().isBadGateway());
    }
}
