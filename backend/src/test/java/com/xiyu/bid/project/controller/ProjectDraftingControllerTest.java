// Input: 模拟 HTTP 请求 (PATCH leads / POST advance / GET) + CO-315 方法级鉴权反射检查 + CO-373 提交投标角色白名单反射检查
// Output: 验证路由、状态码（200/409）与审核人入口不再被 legacy role 白名单拦截；提交投标端点允许 BID_PROJECTLEADER
// Pos: backend test source - 单元级 MockMvc (standalone)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.project.dto.DraftingApprovalRequest;
import com.xiyu.bid.project.dto.DraftingRejectionRequest;
import com.xiyu.bid.project.dto.ProjectDraftingViewDto;
import com.xiyu.bid.project.dto.ProjectLeadAssignmentRequest;
import com.xiyu.bid.project.service.ProjectDraftingService;
import com.xiyu.bid.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectDraftingControllerTest {

    private ProjectDraftingService service;
    private AuthService authService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        service = mock(ProjectDraftingService.class);
        authService = mock(AuthService.class);
        when(authService.resolveUserIdByUsername("admin")).thenReturn(42L);
        ProjectDraftingController controller = new ProjectDraftingController(service, authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        UserDetails principal = User.withUsername("admin").password("x").roles("ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void patch_leads_ok() throws Exception {
        when(service.assignLeads(eq(1L), any(ProjectLeadAssignmentRequest.class), eq(42L)))
                .thenReturn(ProjectDraftingViewDto.builder()
                        .projectId(1L).primaryLeadUserId(10L).secondaryLeadUserId(20L)
                        .gateReady(true).incompleteTaskCount(0).build());
        mockMvc.perform(patch("/api/projects/1/drafting/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ProjectLeadAssignmentRequest.builder()
                                        .primaryLeadUserId(10L).secondaryLeadUserId(20L).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.primaryLeadUserId").value(10));
    }

    @Test
    void post_advance_conflict_returns409() throws Exception {
        when(service.gateAdvanceToEvaluation(eq(1L), eq(42L)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "仍有 2 个任务未完成"));
        mockMvc.perform(post("/api/projects/1/drafting/advance"))
                .andExpect(status().isConflict());
    }

    @Test
    void post_advance_happy() throws Exception {
        when(service.gateAdvanceToEvaluation(eq(1L), eq(42L)))
                .thenReturn(ProjectDraftingViewDto.builder()
                        .projectId(1L).gateReady(true).incompleteTaskCount(0).build());
        mockMvc.perform(post("/api/projects/1/drafting/advance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gateReady").value(true));
    }

    @Test
    void get_returns_view() throws Exception {
        when(service.get(1L)).thenReturn(ProjectDraftingViewDto.builder()
                .projectId(1L).primaryLeadUserId(10L).gateReady(false).incompleteTaskCount(2).build());
        mockMvc.perform(get("/api/projects/1/drafting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.incompleteTaskCount").value(2));
    }

    /** CO-315: 标书审核人可能是 bid_other_dept 等不兼容 legacy STAFF 的角色，控制器不应再拦。 */
    @Test
    void get_accessible_for_bid_other_dept_reviewer() throws Exception {
        UserDetails reviewer = User.withUsername("09118").password("x")
                .authorities("bid-otherDept", "ROLE_BID_OTHERDEPT")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(reviewer, "x", reviewer.getAuthorities()));
        when(authService.resolveUserIdByUsername("09118")).thenReturn(5472L);
        when(service.get(1L)).thenReturn(ProjectDraftingViewDto.builder()
                .projectId(1L).reviewerId(5472L).reviewStatus("reviewing").build());
        mockMvc.perform(get("/api/projects/1/drafting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewerId").value(5472));
    }

    /** CO-315: 审核人执行 approve/reject 不应被 legacy role 白名单拦截。 */
    @Test
    void post_approve_accessible_for_bid_other_dept_reviewer() throws Exception {
        UserDetails reviewer = User.withUsername("09118").password("x")
                .authorities("bid-otherDept", "ROLE_BID_OTHERDEPT")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(reviewer, "x", reviewer.getAuthorities()));
        when(authService.resolveUserIdByUsername("09118")).thenReturn(5472L);
        when(service.approveBid(1L, 5472L, "")).thenReturn(ProjectDraftingViewDto.builder()
                .projectId(1L).reviewStatus("approved").build());
        mockMvc.perform(post("/api/projects/1/drafting/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("approved"));
    }

    @Test
    void post_reject_accessible_for_bid_other_dept_reviewer() throws Exception {
        UserDetails reviewer = User.withUsername("09118").password("x")
                .authorities("bid-otherDept", "ROLE_BID_OTHERDEPT")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(reviewer, "x", reviewer.getAuthorities()));
        when(authService.resolveUserIdByUsername("09118")).thenReturn(5472L);
        when(service.rejectBid(1L, 5472L, "reason")).thenReturn(ProjectDraftingViewDto.builder()
                .projectId(1L).reviewStatus("rejected").build());
        mockMvc.perform(post("/api/projects/1/drafting/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"reason\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("rejected"));
    }

    @Test
    void co315_reviewer_endpoints_do_not_keep_legacy_role_preauthorize() throws Exception {
        assertNull(ProjectDraftingController.class
                .getMethod("get", Long.class)
                .getAnnotation(PreAuthorize.class));
        assertNull(ProjectDraftingController.class
                .getMethod("approve", Long.class, DraftingApprovalRequest.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class));
        assertNull(ProjectDraftingController.class
                .getMethod("reject", Long.class, DraftingRejectionRequest.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class));
    }

    /** CO-373: 提交标书审核/提交投标端点必须允许 BID_PROJECTLEADER（投标项目负责人）。
     *  服务层 BidSubmissionAuthorizationPolicy 已允许该角色匹配 primaryLeadUserId 后提交，
     *  Controller @PreAuthorize 白名单须与服务层 SUBMIT_BID_ALLOWED_ROLES 对齐，否则 403。 */
    @Test
    void co373_submit_endpoints_allow_bid_project_leader() throws Exception {
        PreAuthorize submitBid = ProjectDraftingController.class
                .getMethod("submitBid", Long.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize submitReview = ProjectDraftingController.class
                .getMethod("submitForReview", Long.class, Map.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(submitBid, "submit-bid 必须保留 @PreAuthorize（CO-373）");
        assertNotNull(submitReview, "submit-review 必须保留 @PreAuthorize（CO-373）");
        assertTrue(submitBid.value().contains("BID_PROJECTLEADER"),
                "submit-bid @PreAuthorize 必须包含 BID_PROJECTLEADER（CO-373）");
        assertTrue(submitReview.value().contains("BID_PROJECTLEADER"),
                "submit-review @PreAuthorize 必须包含 BID_PROJECTLEADER（CO-373）");
    }
}
