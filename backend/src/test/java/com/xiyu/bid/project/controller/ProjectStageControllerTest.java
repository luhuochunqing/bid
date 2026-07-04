// Input: HTTP GET /api/projects/{id}/stage + 当前用户
// Output: StageViewDto 包含当前用户可访问阶段；CO-315 审核人可进入 DRAFTING
// Pos: project/controller/ - WS-G 阶段快照入口测试
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.controller;

import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.entity.ProjectClosure;
import com.xiyu.bid.project.repository.ProjectClosureRepository;
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
    private ProjectClosureRepository closureRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        stageService = mock(ProjectStageService.class);
        bidReviewAppService = mock(BidReviewAppService.class);
        projectAccessScopeService = mock(ProjectAccessScopeService.class);
        authService = mock(AuthService.class);
        closureRepository = mock(ProjectClosureRepository.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProjectStageController(
                        stageService,
                        bidReviewAppService,
                        projectAccessScopeService,
                        authService,
                        closureRepository
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
                new BidReviewAppService.ReviewState("REVIEWING", 5472L, null, "覃超颖", List.of()));

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
                new BidReviewAppService.ReviewState("REVIEWING", 5472L, null, "覃超颖", List.of()));

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
                new BidReviewAppService.ReviewState("REVIEWING", 9999L, null, "其他人", List.of()));
        // CO-443 修正: 结项申请已提交但审批中(PENDING) → terminal=false（进行中）
        ProjectClosure pendingClosure = ProjectClosure.builder().reviewStatus("PENDING").build();
        when(closureRepository.findByProjectId(42L)).thenReturn(java.util.Optional.of(pendingClosure));

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
                new BidReviewAppService.ReviewState("REVIEWING", 9999L, null, "其他人", List.of()));
        // CO-443 修正: 结项审批通过(APPROVED) → terminal=true（已完成）
        ProjectClosure approvedClosure = ProjectClosure.builder().reviewStatus("APPROVED").build();
        when(closureRepository.findByProjectId(42L)).thenReturn(java.util.Optional.of(approvedClosure));

        mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStage").value("CLOSED"))
                .andExpect(jsonPath("$.data.terminal").value(true));
    }


    // CO-443 修正: 复盘提交后 stage 停在 RETROSPECTIVE（不直达 CLOSED），未提交结项申请(无 closure)
    // → currentStage=RETROSPECTIVE, terminal=false（进行中，非已完成）
    // 修复前 RetrospectiveService.submit() 直达 CLOSED，导致 ClosureStage 的"提交结项"按钮被隐藏。
    @Test
    void co443_retrospectiveDone_noClosure_showsInProgress() throws Exception {
        authenticate("09118");
        when(authService.resolveUserIdByUsername("09118")).thenReturn(5472L);
        when(stageService.currentStage(42L)).thenReturn(ProjectStage.RETROSPECTIVE);
        when(stageService.hasClosureSubmission(42L)).thenReturn(false);
        when(bidReviewAppService.getReviewState(42L)).thenReturn(
                new BidReviewAppService.ReviewState("REVIEWING", 9999L, null, "其他人", List.of()));
        when(closureRepository.findByProjectId(42L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStage").value("RETROSPECTIVE"))
                .andExpect(jsonPath("$.data.terminal").value(false));
    }

    // CO-498: 复盘阶段(stage=RETROSPECTIVE) 且未提交结项申请(无 closure) 时，
    // 必须把 CLOSED 加入 accessibleStages，让项目负责人能进入结项 tab 提交结项申请。
    // 否则整个结项审核流程从源头死锁（5d1b36b53 暴露的下游导航断层）。
    // 不按角色区分 — 角色矩阵下沉到 ClosureStage.canSubmitClosure/canApprove。
    @Test
    void co498_retrospectiveWithoutClosure_unlocksClosedTab() throws Exception {
        authenticate("06234");
        when(authService.resolveUserIdByUsername("06234")).thenReturn(100L);
        when(stageService.currentStage(42L)).thenReturn(ProjectStage.RETROSPECTIVE);
        when(stageService.hasClosureSubmission(42L)).thenReturn(false);
        when(stageService.allowedNext(42L)).thenReturn(List.of(ProjectStage.CLOSED));
        when(bidReviewAppService.getReviewState(42L)).thenReturn(
                new BidReviewAppService.ReviewState("REVIEWING", 9999L, null, "其他人", List.of()));
        when(closureRepository.findByProjectId(42L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStage").value("RETROSPECTIVE"))
                .andExpect(jsonPath("$.data.terminal").value(false))
                .andExpect(jsonPath("$.data.accessibleStages", org.hamcrest.Matchers.hasItem("CLOSED")));
    }

    // CO-498 边界守护: 非 RETROSPECTIVE 阶段(如 RESULT_PENDING) 不应解锁 CLOSED tab。
    // 此测试在当前实现下应通过(回归保障)，主要防止后续 refactor 误把解锁逻辑泛化。
    @Test
    void co498_resultPendingStage_doesNotUnlockClosedTab() throws Exception {
        authenticate("06234");
        when(authService.resolveUserIdByUsername("06234")).thenReturn(100L);
        when(stageService.currentStage(42L)).thenReturn(ProjectStage.RESULT_PENDING);
        when(stageService.hasClosureSubmission(42L)).thenReturn(false);
        when(stageService.allowedNext(42L)).thenReturn(List.of(ProjectStage.RETROSPECTIVE));
        when(bidReviewAppService.getReviewState(42L)).thenReturn(
                new BidReviewAppService.ReviewState("REVIEWING", 9999L, null, "其他人", List.of()));

        mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessibleStages",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("CLOSED"))));
    }

    // CO-498 重复守护: stage=RETROSPECTIVE 但已提交 closure(DRAFT) 时，
    // CO-443 假 CLOSED 机制把 current 改成 CLOSED → CLOSED 已在 completed 列表里。
    // 解锁逻辑必须用 actual==RETROSPECTIVE 精确判定，避免 CLOSED 在 accessibleStages 出现两次。
    @Test
    void co498_retrospectiveWithClosureDraft_doesNotUnlockClosedTwice() throws Exception {
        authenticate("06234");
        when(authService.resolveUserIdByUsername("06234")).thenReturn(100L);
        when(stageService.currentStage(42L)).thenReturn(ProjectStage.RETROSPECTIVE);
        when(stageService.hasClosureSubmission(42L)).thenReturn(true);
        when(bidReviewAppService.getReviewState(42L)).thenReturn(
                new BidReviewAppService.ReviewState("REVIEWING", 9999L, null, "其他人", List.of()));
        ProjectClosure draftClosure = ProjectClosure.builder().reviewStatus("DRAFT").build();
        when(closureRepository.findByProjectId(42L)).thenReturn(java.util.Optional.of(draftClosure));

        mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // CO-443 假 CLOSED: current 改成 CLOSED（hasClosureSubmission=true）
                .andExpect(jsonPath("$.data.currentStage").value("CLOSED"))
                // CLOSED 已在 completed 列表里(出现一次)；不应重复出现导致 accessibleStages 有 7 项
                .andExpect(jsonPath("$.data.accessibleStages", org.hamcrest.Matchers.hasItem("CLOSED")))
                .andExpect(jsonPath("$.data.accessibleStages",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasSize(7))));
    }

    // CO-498 角色一致性: 解锁对所有通过项目权限校验的成员一致，不按角色区分。
    // 06234(admin) 和 09118(审核人) 在同一状态下看到的 accessibleStages 都应包含 CLOSED。
    @Test
    void co498_retrospectiveWithoutClosure_unlocksClosedForAllRoles() throws Exception {
        for (String username : new String[]{"06234", "09118"}) {
            authenticate(username);
            when(authService.resolveUserIdByUsername(username)).thenReturn(100L);
            when(stageService.currentStage(42L)).thenReturn(ProjectStage.RETROSPECTIVE);
            when(stageService.hasClosureSubmission(42L)).thenReturn(false);
            when(stageService.allowedNext(42L)).thenReturn(List.of(ProjectStage.CLOSED));
            when(bidReviewAppService.getReviewState(42L)).thenReturn(
                    new BidReviewAppService.ReviewState("REVIEWING", 9999L, null, "其他人", List.of()));
            when(closureRepository.findByProjectId(42L)).thenReturn(java.util.Optional.empty());

            mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessibleStages", org.hamcrest.Matchers.hasItem("CLOSED")));
        }
    }

    private void authenticate(String username) {
        UserDetails user = User.withUsername(username).password("x").authorities("bid-otherDept").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "x", user.getAuthorities()));
    }
}
