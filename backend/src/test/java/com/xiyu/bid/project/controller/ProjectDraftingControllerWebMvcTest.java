// Input: 模拟 HTTP 请求 (POST approve / reject) + 路径参数 + JSON body
// Output: 验证 Drafting 接口契约（@Valid + DTO + comment 字段）的反序列化/校验行为
// Pos: backend test source - @WebMvcTest 切片
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.project.dto.ProjectDraftingViewDto;
import com.xiyu.bid.project.service.ProjectDraftingService;
import com.xiyu.bid.security.CurrentUserLookupService;
import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProjectDraftingController 审批接口契约测试。参照 docs/architecture/approval-contract.md。
 * <p>覆盖 approve/reject 的反序列化 + 校验场景：
 * <ul>
 *   <li>空 body / 空字符串 → 400（@RequestBody 必填）</li>
 *   <li>approve {@code {"comment":""}} → 200（通过操作允许空意见）</li>
 *   <li>reject {@code {"comment":""}} → 400（@NotBlank 驳回必填）</li>
 * </ul>
 */
@WebMvcTest(controllers = ProjectDraftingController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@AutoConfigureMockMvc(addFilters = false)
class ProjectDraftingControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectDraftingService service;

    @MockBean
    private AuthService authService;

    // CO-373: CurrentUserResolver 依赖链在 @WebMvcTest 切片不实例化；
    // TraceFilter(@Component) 强依赖 CurrentUserResolver，mock 整个 resolver 满足注入。
    @MockBean
    private CurrentUserResolver currentUserResolver;

    @MockBean
    private CurrentUserLookupService currentUserLookupService;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("approve 空 body 返回 400")
    void approve_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/projects/1/drafting/approve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("approve 空字符串 body 被 Jackson 解析为空 POJO，返回 200（通过操作允许空意见）")
    void approve_emptyStringParsedAsEmptyObject_returns200() throws Exception {
        // 注意：Spring Boot 默认 Jackson 配置会把 JSON 空字符串 "" 解析为空 POJO（comment=null），
        // Controller 中 request.getComment() != null ? request.getComment() : "" 兜底为空串。
        when(authService.resolveUserIdByUsername("user")).thenReturn(1L);
        when(service.approveBid(eq(1L), eq(1L), eq("")))
                .thenReturn(ProjectDraftingViewDto.builder()
                        .projectId(1L).reviewStatus("approved").build());
        mockMvc.perform(post("/api/projects/1/drafting/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"\""))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("approve comment 为空字符串返回 200（通过操作允许空意见）")
    void approve_blankComment_returns200() throws Exception {
        when(authService.resolveUserIdByUsername("user")).thenReturn(1L);
        when(service.approveBid(eq(1L), eq(1L), eq("")))
                .thenReturn(ProjectDraftingViewDto.builder()
                        .projectId(1L).reviewStatus("approved").build());
        mockMvc.perform(post("/api/projects/1/drafting/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("approve 正常 comment 返回 200")
    void approve_validComment_returns200() throws Exception {
        when(authService.resolveUserIdByUsername("user")).thenReturn(1L);
        when(service.approveBid(eq(1L), eq(1L), eq("同意")))
                .thenReturn(ProjectDraftingViewDto.builder()
                        .projectId(1L).reviewStatus("approved").build());
        mockMvc.perform(post("/api/projects/1/drafting/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("reject 空 body 返回 400")
    void reject_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/projects/1/drafting/reject")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("reject comment 为空字符串返回 400（驳回必填）")
    void reject_blankComment_returns400() throws Exception {
        mockMvc.perform(post("/api/projects/1/drafting/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("reject 正常 comment 返回 200")
    void reject_validComment_returns200() throws Exception {
        when(authService.resolveUserIdByUsername("user")).thenReturn(1L);
        when(service.rejectBid(eq(1L), eq(1L), eq("不行")))
                .thenReturn(ProjectDraftingViewDto.builder()
                        .projectId(1L).reviewStatus("rejected").build());
        mockMvc.perform(post("/api/projects/1/drafting/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"不行\"}"))
                .andExpect(status().isOk());
    }
}
