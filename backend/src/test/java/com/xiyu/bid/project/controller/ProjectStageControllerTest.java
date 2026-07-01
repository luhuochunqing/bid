// Input: HTTP GET /api/projects/{id}/stage + 当前用户
// Output: StageViewDto 包含当前用户可访问阶段；CO-315 审核人可进入 DRAFTING
// Pos: project/controller/ - WS-G 阶段快照入口测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.service.BidReviewAppService;
import com.xiyu.bid.project.service.ProjectStageService;
import com.xiyu.bid.service.AuthService;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectStageControllerTest {

    private ProjectStageService stageService;
    private BidReviewAppService bidReviewAppService;
    private ProjectAccessScopeService projectAccessScopeService;
    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        stageService = mock(ProjectStageService.class);
        bidReviewAppService = mock(BidReviewAppService.class);
        projectAccessScopeService = mock(ProjectAccessScopeService.class);
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProjectStageController(
                        stageService,
                        bidReviewAppService,
                        projectAccessScopeService,
                        authService
                ))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.clearContext();
    }

    @Test
    void co315_reviewer_stage_snapshot_exposes_drafting_as_accessible_default_stage() throws Exception {
        authenticate("09118");
        when(authService.resolveUserIdByUsername("09118")).thenReturn(5472L);
        when(stageService.currentStage(42L)).thenReturn(ProjectStage.INITIATED);
        when(stageService.hasClosureSubmission(42L)).thenReturn(false);
        when(stageService.allowedNext(42L)).thenReturn(List.of(ProjectStage.DRAFTING));
        when(bidReviewAppService.getReviewState(42L)).thenReturn(
                new BidReviewAppService.ReviewState("REVIEWING", 5472L, null, "覃超颖"));

        mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStage").value("INITIATED"))
                .andExpect(jsonPath("$.data.accessibleStages[0]").value("INITIATED"))
                .andExpect(jsonPath("$.data.accessibleStages[1]").value("DRAFTING"))
                .andExpect(jsonPath("$.data.defaultOpenStage").value("DRAFTING"));

        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(42L);
    }

    @Test
    void non_reviewer_stage_snapshot_keeps_linear_accessible_stage() throws Exception {
        authenticate("06234");
        when(authService.resolveUserIdByUsername("06234")).thenReturn(100L);
        when(stageService.currentStage(42L)).thenReturn(ProjectStage.EVALUATING);
        when(stageService.hasClosureSubmission(42L)).thenReturn(false);
        when(stageService.allowedNext(42L)).thenReturn(List.of(ProjectStage.RESULT_PENDING));
        when(bidReviewAppService.getReviewState(42L)).thenReturn(
                new BidReviewAppService.ReviewState("REVIEWING", 5472L, null, "覃超颖"));

        mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStage").value("EVALUATING"))
                .andExpect(jsonPath("$.data.accessibleStages[0]").value("INITIATED"))
                .andExpect(jsonPath("$.data.accessibleStages[1]").value("DRAFTING"))
                .andExpect(jsonPath("$.data.accessibleStages[2]").value("EVALUATING"))
                .andExpect(jsonPath("$.data.defaultOpenStage").value("EVALUATING"));
    }

    // CO-443: 提交结项申请后（审批中），currentStage 应覆盖为 CLOSED
    @Test
    void co443_closureSubmitted_showsClosedAsCurrentStage() throws Exception {
        authenticate("09118");
        when(authService.resolveUserIdByUsername("09118")).thenReturn(5472L);
        when(stageService.currentStage(42L)).thenReturn(ProjectStage.RETROSPECTIVE);
        when(stageService.hasClosureSubmission(42L)).thenReturn(true);
        when(bidReviewAppService.getReviewState(42L)).thenReturn(
                new BidReviewAppService.ReviewState("REVIEWING", 9999L, null, "其他人"));

        mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStage").value("CLOSED"))
                .andExpect(jsonPath("$.data.terminal").value(false))
                .andExpect(jsonPath("$.data.defaultOpenStage").value("CLOSED"));
    }

    // CO-443: 审批通过后 project.stage=CLOSED，terminal=true
    @Test
    void co443_closureApproved_showsClosedTerminal() throws Exception {
        authenticate("09118");
        when(authService.resolveUserIdByUsername("09118")).thenReturn(5472L);
        when(stageService.currentStage(42L)).thenReturn(ProjectStage.CLOSED);
        when(stageService.hasClosureSubmission(42L)).thenReturn(true);
        when(bidReviewAppService.getReviewState(42L)).thenReturn(
                new BidReviewAppService.ReviewState("REVIEWING", 9999L, null, "其他人"));

        mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStage").value("CLOSED"))
                .andExpect(jsonPath("$.data.terminal").value(true));
    }

    private void authenticate(String username) {
        UserDetails user = User.withUsername(username).password("x").authorities("bid-otherDept").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "x", user.getAuthorities()));
    }
}
