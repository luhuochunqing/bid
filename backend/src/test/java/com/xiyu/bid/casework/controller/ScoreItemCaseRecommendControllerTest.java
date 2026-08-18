package com.xiyu.bid.casework.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.casework.application.service.ScoreItemCaseRecommendAppService;
import com.xiyu.bid.casework.dto.ScoreItemCaseRecommendRequest;
import com.xiyu.bid.casework.dto.ScoreItemCaseRecommendResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(ScoreItemCaseRecommendControllerTest.MethodSecurityConfig.class)
@WebMvcTest(controllers = ScoreItemCaseRecommendController.class,
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
class ScoreItemCaseRecommendControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScoreItemCaseRecommendAppService recommendAppService;

    @MockBean
    private com.xiyu.bid.service.ProjectAccessScopeService projectAccessScopeService;

    @MockBean
    private com.xiyu.bid.security.CurrentUserResolver currentUserResolver;

    @MockBean
    private com.xiyu.bid.security.EffectiveRoleResolver effectiveRoleResolver;

    @Test
    @WithMockUser
    @DisplayName("POST /api/projects/{projectId}/cases/recommend 成功返回推荐列表")
    void testRecommendCasesSuccess() throws Exception {
        ScoreItemCaseRecommendResponse mockResponse = ScoreItemCaseRecommendResponse.builder()
                .total(1)
                .items(List.of(
                        ScoreItemCaseRecommendResponse.Item.builder()
                                .id(1001L)
                                .sourceProjectId(501L)
                                .sourceProjectName("某智能电网项目")
                                .scoringPointTitle("高可用系统架构设计")
                                .scoringCategory("技术方案")
                                .matchScore(95)
                                .matchLabel("优质")
                                .matchReasons(List.of("评分项条款高度契合", "历史中标案例"))
                                .matchedKeywords(List.of("双活容灾", "高可用"))
                                .requirementRaw("投标人须提供高可用架构方案")
                                .responseText("我方采用同城双活部署...")
                                .responseTextSummary("我方采用同城双活部署...")
                                .bidResult("WON")
                                .reuseCount(8)
                                .isPinned(false)
                                .build()
                ))
                .build();

        when(recommendAppService.recommend(eq(123L), any(ScoreItemCaseRecommendRequest.class)))
                .thenReturn(mockResponse);

        ScoreItemCaseRecommendRequest request = ScoreItemCaseRecommendRequest.builder()
                .scoreItemId(10L)
                .keyword("双活容灾")
                .page(0)
                .size(10)
                .build();

        mockMvc.perform(post("/api/projects/123/cases/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(1001))
                .andExpect(jsonPath("$.items[0].matchScore").value(95))
                .andExpect(jsonPath("$.items[0].matchLabel").value("优质"))
                .andExpect(jsonPath("$.items[0].scoringPointTitle").value("高可用系统架构设计"))
                .andExpect(jsonPath("$.items[0].matchedKeywords[0]").value("双活容灾"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/projects/{projectId}/cases/recommend 无请求体默认成功")
    void testRecommendCasesEmptyBody() throws Exception {
        ScoreItemCaseRecommendResponse mockResponse = ScoreItemCaseRecommendResponse.builder()
                .total(0)
                .items(List.of())
                .build();

        when(recommendAppService.recommend(eq(123L), any(ScoreItemCaseRecommendRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/projects/123/cases/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/projects/{projectId}/score-items 成功返回评分项列表")
    void testGetScoreItemsSuccess() throws Exception {
        com.xiyu.bid.scoreparse.entity.ScoreItem item = new com.xiyu.bid.scoreparse.entity.ScoreItem();
        item.setId(10L);
        item.setCode("A1");
        item.setDim("技术方案");
        item.setDetail("系统架构高可用设计");

        when(recommendAppService.getScoreItems(eq(123L))).thenReturn(List.of(item));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/projects/123/score-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].code").value("A1"))
                .andExpect(jsonPath("$[0].dim").value("技术方案"));
    }
}
