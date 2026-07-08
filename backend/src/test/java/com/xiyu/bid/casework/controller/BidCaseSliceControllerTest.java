package com.xiyu.bid.casework.controller;

import com.xiyu.bid.casework.application.BidCaseSliceDetail;
import com.xiyu.bid.casework.application.service.BatchEmbeddingAppService;
import com.xiyu.bid.casework.application.service.BidCaseSliceRecommendAppService;
import com.xiyu.bid.casework.domain.model.BidCaseSliceRecommendation;
import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(BidCaseSliceControllerTest.MethodSecurityConfig.class)
@WebMvcTest(controllers = BidCaseSliceController.class,
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
class BidCaseSliceControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BidCaseSliceRecommendAppService appService;

    @MockBean
    private BatchEmbeddingAppService batchEmbeddingAppService;

    @MockBean
    private ProjectAccessScopeService projectAccessScopeService;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    // Phase 3 US3 修复：TraceFilter 现依赖 EffectiveRoleResolver（MDC userId/roleCode 填充），
    // @WebMvcTest 切片不实例化该 bean，需手动 mock 以满足 TraceFilter 注入。
    @MockBean
    private com.xiyu.bid.security.EffectiveRoleResolver effectiveRoleResolver;

    @Test
    @WithMockUser
    void recommendByScoringItem_shouldReturnOkWithData() throws Exception {
        BidCaseSliceRecommendation recommendation = new BidCaseSliceRecommendation(
                123L, "2026.01.05-中广核办公", "技术文件/中广核办公技术方案.docx", "技术",
                "狮行物流技术与系统优势", "强大的计划管理系统PMS...", 308, 5,
                0.872, 88, "语义相似、标题匹配、技术文件"
        );
        when(appService.recommendByScoringItem(eq(1L), eq(2L), any())).thenReturn(List.of(recommendation));

        mockMvc.perform(get("/api/case-slices/recommend")
                        .param("projectId", "1")
                        .param("scoringItemId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].sliceId").value(123))
                .andExpect(jsonPath("$.data[0].finalScore").value(88))
                .andExpect(jsonPath("$.data[0].matchReason").value("语义相似、标题匹配、技术文件"));
    }

    @Test
    @WithMockUser
    void recommendByScoringItem_missingProjectId_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/case-slices/recommend")
                        .param("scoringItemId", "2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"system.admin"})
    void recommendByQuery_shouldReturnOkWithData() throws Exception {
        BidCaseSliceRecommendation recommendation = new BidCaseSliceRecommendation(
                456L, "p1", "商务.docx", "商务", "售后服务保障", "正文", 200, 4,
                0.85, 85, "语义相似"
        );
        when(appService.recommendByQuery(eq("售后服务保障措施"), any())).thenReturn(List.of(recommendation));

        mockMvc.perform(get("/api/case-slices/recommend/by-query")
                        .param("query", "售后服务保障措施"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].sliceId").value(456));
    }

    @Test
    @WithMockUser
    void recommendByQuery_withoutAdminAuthority_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/case-slices/recommend/by-query")
                        .param("query", "售后服务保障措施"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void recommendByQuery_missingQuery_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/case-slices/recommend/by-query"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getSliceDetail_shouldReturnOkWithData() throws Exception {
        BidCaseSliceDetail detail = new BidCaseSliceDetail(
                123L, "2026.01.05-中广核办公", "技术文件/中广核办公技术方案.docx", "技术",
                "狮行物流技术与系统优势", "强大的计划管理系统PMS...", 308, 5,
                LocalDateTime.of(2026, 7, 4, 10, 0, 0)
        );
        when(appService.getSliceDetail(123L, 1L)).thenReturn(detail);

        mockMvc.perform(get("/api/case-slices/123")
                        .param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sliceId").value(123))
                .andExpect(jsonPath("$.data.sectionTitle").value("狮行物流技术与系统优势"));
    }

    @Test
    @WithMockUser
    void getSliceDetail_missingProjectId_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/case-slices/123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"system.admin"})
    void batchEmbed_shouldReturnOkWithSummary() throws Exception {
        when(batchEmbeddingAppService.embedAllUnprocessed(100))
                .thenReturn(new BatchEmbeddingAppService.EmbeddingResult(100, 2, 8042));

        mockMvc.perform(post("/api/case-slices/admin/batch-embed")
                        .param("batchSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processed").value(100))
                .andExpect(jsonPath("$.data.failed").value(2))
                .andExpect(jsonPath("$.data.remaining").value(8042));
    }

    @Test
    @WithMockUser
    void batchEmbed_withoutAdminAuthority_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/case-slices/admin/batch-embed")
                        .param("batchSize", "100"))
                .andExpect(status().isForbidden());
    }
}
