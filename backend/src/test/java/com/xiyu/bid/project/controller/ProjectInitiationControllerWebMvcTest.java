// Input: 模拟 HTTP 请求 (POST approve / reject) + 路径参数 + JSON body
// Output: 验证 Initiation 接口契约（@Valid + DTO + comment 字段）的反序列化/校验行为
// Pos: backend test source - @WebMvcTest 切片
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.config.RateLimitFilter;
import com.xiyu.bid.config.SecurityConfig;
import com.xiyu.bid.project.service.ProjectCurrentUserLookupService;
import com.xiyu.bid.project.service.ProjectInitiationApprovalService;
import com.xiyu.bid.project.service.ProjectInitiationService;
import com.xiyu.bid.security.CurrentUserLookupService;
import com.xiyu.bid.security.CurrentUserResolver;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProjectInitiationController 审批接口契约测试。参照 docs/architecture/approval-contract.md。
 * <p>覆盖 approve/reject 的反序列化 + 校验场景：
 * <ul>
 *   <li>approve 空 body → 400（@RequestBody 必填）</li>
 *   <li>approve 缺 primaryLeadUserId → 400（@NotNull）</li>
 *   <li>approve 完整（primaryLeadUserId + comment）→ 200</li>
 *   <li>approve 仅 primaryLeadUserId 无 comment → 200（comment 可选）</li>
 *   <li>reject 空 body → 400</li>
 *   <li>reject {@code {"comment":""}} → 400（@NotBlank）</li>
 *   <li>reject {@code {"comment":"不行"}} → 200</li>
 * </ul>
 *
 * <p>权限矩阵注解契约由 ProjectInitiationPermissionTest 锁定，不在本切片测试中重复。
 */
@WebMvcTest(controllers = ProjectInitiationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
                        ApiKeyAuthenticationFilter.class}
        ))
@AutoConfigureMockMvc(addFilters = false)
class ProjectInitiationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectInitiationService service;

    @MockBean
    private ProjectInitiationApprovalService approvalService;

    @MockBean
    private ProjectCurrentUserLookupService projectCurrentUserLookupService;

    // CO-373: CurrentUserResolver 依赖链在 @WebMvcTest 切片不实例化；
    // TraceFilter(@Component) 强依赖 CurrentUserResolver，mock 整个 resolver 满足注入。
    @MockBean
    private CurrentUserResolver currentUserResolver;

    @MockBean
    private CurrentUserLookupService securityCurrentUserLookupService;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("approve 空 body 返回 400")
    void approve_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/projects/1/initiation/approve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("approve 缺 primaryLeadUserId 返回 400（@NotNull）")
    void approve_missingPrimaryLead_returns400() throws Exception {
        mockMvc.perform(post("/api/projects/1/initiation/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("approve 仅 primaryLeadUserId 无 comment 返回 200（comment 可选）")
    void approve_onlyPrimaryLead_returns200() throws Exception {
        when(projectCurrentUserLookupService.requireUserId(any())).thenReturn(1L);
        doNothing().when(approvalService).approve(eq(1L), any(), eq(1L));
        mockMvc.perform(post("/api/projects/1/initiation/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryLeadUserId\":100}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("approve primaryLeadUserId + comment 返回 200")
    void approve_validRequest_returns200() throws Exception {
        when(projectCurrentUserLookupService.requireUserId(any())).thenReturn(1L);
        doNothing().when(approvalService).approve(eq(1L), any(), eq(1L));
        mockMvc.perform(post("/api/projects/1/initiation/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryLeadUserId\":100,\"comment\":\"同意\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("reject 空 body 返回 400")
    void reject_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/projects/1/initiation/reject")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("reject comment 为空字符串返回 400（@NotBlank）")
    void reject_blankComment_returns400() throws Exception {
        mockMvc.perform(post("/api/projects/1/initiation/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("reject 正常 comment 返回 200")
    void reject_validComment_returns200() throws Exception {
        when(projectCurrentUserLookupService.requireUserId(any())).thenReturn(1L);
        doNothing().when(approvalService).reject(eq(1L), any(), eq(1L));
        mockMvc.perform(post("/api/projects/1/initiation/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"不行\"}"))
                .andExpect(status().isOk());
    }
}
