// Input: ProjectDraftingService 行为
// Output: Mockito 单元测试覆盖 assignLeads + gateAdvanceToEvaluation + submitBid
// Pos: backend test source
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.crm.application.OssPermissionCache;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.dto.ProjectLeadAssignmentRequest;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.project.repository.ProjectEvaluationRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class ProjectDraftingServiceTest {

    @Mock ProjectLeadAssignmentRepository leadRepo;
    @Mock ProjectRepository projectRepository;
    @Mock TaskRepository taskRepository;
    @Mock ProjectStageService projectStageService;
    @Mock com.xiyu.bid.service.ProjectAccessScopeService projectAccessScopeService;
    @Mock BidReviewAppService bidReviewAppService;
    @Mock UserRepository userRepository;
    @Mock ProjectEvaluationRepository projectEvaluationRepository;
    @Mock ProjectNotificationService notificationService;
    @Mock ProjectDocumentRepository projectDocumentRepository;
    @Mock OssPermissionCache ossPermissionCache;

    ProjectDraftingService service;

    @BeforeEach
    void setUp() {
        service = new ProjectDraftingService(leadRepo, projectRepository, taskRepository, projectStageService, projectAccessScopeService, userRepository, bidReviewAppService, projectEvaluationRepository, notificationService, projectDocumentRepository, ossPermissionCache);
        lenient().when(projectRepository.findById(1L))
                .thenReturn(Optional.of(Project.builder().id(1L).build()));
        lenient().when(leadRepo.save(any(ProjectLeadAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(projectStageService.currentStage(1L)).thenReturn(ProjectStage.DRAFTING);
        lenient().when(bidReviewAppService.getReviewState(1L))
                .thenReturn(new BidReviewAppService.ReviewState("DRAFT", null, null, null));
    }

    @Test
    void assignLeads_happy_createsAndReturns() {
        when(leadRepo.findByProjectId(1L)).thenReturn(Optional.empty());
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of());
        var view = service.assignLeads(1L,
                ProjectLeadAssignmentRequest.builder()
                        .primaryLeadUserId(10L).secondaryLeadUserId(20L).build(),
                99L);
        assertThat(view.getPrimaryLeadUserId()).isEqualTo(10L);
        assertThat(view.getSecondaryLeadUserId()).isEqualTo(20L);
        assertThat(view.getGateReady()).isTrue();
    }

    @Test
    void assignLeads_bidSpecialistAsPrimaryLead_allowed() {
        when(leadRepo.findByProjectId(1L)).thenReturn(Optional.empty());
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of());
        var view = service.assignLeads(1L,
                ProjectLeadAssignmentRequest.builder().primaryLeadUserId(10L).build(), 99L);
        assertThat(view.getPrimaryLeadUserId()).isEqualTo(10L);
    }

    @Test
    void assignLeads_missingPrimary_422() {
        assertThatThrownBy(() -> service.assignLeads(1L,
                ProjectLeadAssignmentRequest.builder().secondaryLeadUserId(20L).build(), 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void assignLeads_samePrimaryAndSecondary_422() {
        assertThatThrownBy(() -> service.assignLeads(1L,
                ProjectLeadAssignmentRequest.builder()
                        .primaryLeadUserId(10L).secondaryLeadUserId(10L).build(), 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void gate_allowsWhenNoTasks() {
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of());
        when(leadRepo.findByProjectId(1L)).thenReturn(Optional.empty());
        prepareBidDocument();
        var view = service.gateAdvanceToEvaluation(1L, 99L);
        assertThat(view.getGateReady()).isTrue();
        assertThat(view.getIncompleteTaskCount()).isZero();
    }

    @Test
    void gate_deniesWhenIncomplete_409() {
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.COMPLETED).build(),
                Task.builder().id(2L).projectId(1L).title("b").status(Task.Status.TODO).build()));
        assertThatThrownBy(() -> service.gateAdvanceToEvaluation(1L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void gate_deniesWhenReviewTaskIncomplete() {
        // CO-361: 三态模型收口，REVIEW 不是终态，闸门应拒绝
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.COMPLETED).build(),
                Task.builder().id(2L).projectId(1L).title("b").status(Task.Status.REVIEW).build()));
        assertThatThrownBy(() -> service.gateAdvanceToEvaluation(1L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void assignLeads_atClosedStage_throws423() {
        when(projectStageService.currentStage(1L)).thenReturn(ProjectStage.CLOSED);
        assertThatThrownBy(() -> service.assignLeads(1L,
                ProjectLeadAssignmentRequest.builder().primaryLeadUserId(10L).build(), 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    void get_returnsViewWithLeadsAndGate() {
        when(leadRepo.findByProjectId(1L)).thenReturn(Optional.of(
                ProjectLeadAssignment.builder().id(5L).projectId(1L)
                        .primaryLeadUserId(10L).secondaryLeadUserId(20L).build()));
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.TODO).build()));
        var view = service.get(1L);
        assertThat(view.getPrimaryLeadUserId()).isEqualTo(10L);
        assertThat(view.getGateReady()).isFalse();
        assertThat(view.getIncompleteTaskCount()).isEqualTo(1);
        verify(projectAccessScopeService).assertCurrentUserCanAccessProject(1L);
    }

    @Test
    void get_deniesWhenProjectAccessScopeThrows() {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "无项目访问权限"))
                .when(projectAccessScopeService).assertCurrentUserCanAccessProject(1L);
        assertThatThrownBy(() -> service.get(1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── submitBid 角色校验 ────────────────────────────────────────────────

    private static com.xiyu.bid.entity.RoleProfile roleProfile(String code) {
        return com.xiyu.bid.entity.RoleProfile.builder().code(code).build();
    }

    private com.xiyu.bid.entity.User mockUser(Long id, String roleProfileCode) {
        return com.xiyu.bid.entity.User.builder()
                .id(id)
                .username("test-" + id)
                .role(com.xiyu.bid.entity.User.Role.MANAGER)
                .roleProfile(roleProfileCode != null ? roleProfile(roleProfileCode) : null)
                .build();
    }

    private com.xiyu.bid.entity.User mockOssUser(Long id, String roleProfileCode) {
        return com.xiyu.bid.entity.User.builder()
                .id(id)
                .username("test-" + id)
                .role(com.xiyu.bid.entity.User.Role.MANAGER)
                .roleProfile(roleProfileCode != null ? roleProfile(roleProfileCode) : null)
                .externalOrgSourceApp("oss")
                .build();
    }

    private void prepareSubmitBidHappyPath() {
        lenient().when(taskRepository.findByProjectId(1L)).thenReturn(List.of());
        // 默认 mock：lead 未分配（admin/bid_admin/bid_lead 路径不依赖 lead；sales/bid_specialist 测试按需覆盖）
        lenient().when(leadRepo.findByProjectId(1L)).thenReturn(Optional.empty());
        lenient().when(projectStageService.currentStage(1L)).thenReturn(ProjectStage.DRAFTING);
        lenient().when(bidReviewAppService.getReviewState(1L))
                .thenReturn(new BidReviewAppService.ReviewState("APPROVED", null, null, null));
        lenient().when(projectEvaluationRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        lenient().when(projectEvaluationRepository.save(any(com.xiyu.bid.project.entity.ProjectEvaluation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        prepareBidDocument();
    }

    private void prepareBidDocument() {
        lenient().when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                        eq(1L), eq("BID_DOCUMENT"), eq(null), eq(null)))
                .thenReturn(List.of(ProjectDocument.builder().id(1L).projectId(1L).name("bid.pdf").build()));
    }

    private void prepareNoBidDocument() {
        lenient().when(projectDocumentRepository.findByProjectIdAndFiltersOrderByCreatedAtDesc(
                        eq(1L), eq("BID_DOCUMENT"), eq(null), eq(null)))
                .thenReturn(List.of());
    }

    /** 构造项目级负责人分配 mock：primaryLeadId/secondaryLeadId 任一为 null 表示未分配该角色。 */
    private void prepareLeadAssignment(Long primaryLeadId, Long secondaryLeadId) {
        when(leadRepo.findByProjectId(1L)).thenReturn(Optional.of(
                ProjectLeadAssignment.builder().id(5L).projectId(1L)
                        .primaryLeadUserId(primaryLeadId)
                        .secondaryLeadUserId(secondaryLeadId)
                        .build()));
    }

    @Test
    void submitBid_sales_asPrimaryLead_allowed() {
        // sales（bid-projectLeader）作为 primaryLead 可以提交投标（蓝图 §3.3.1.2）
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-projectLeader")));
        when(ossPermissionCache.getRoleCode("test-1"))
                .thenReturn(java.util.Optional.empty()); // 本地用户无缓存，fallback 到 DB
        prepareLeadAssignment(1L, 2L);  // sales 用户=1 是 primaryLead

        var view = service.submitBid(1L, 1L);

        assertThat(view).isNotNull();
    }

    @Test
    void submitBid_sales_asSecondaryLead_denied_403() {
        // sales 只能匹配 primaryLead，不能匹配 secondaryLead
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-projectLeader")));
        when(ossPermissionCache.getRoleCode("test-1"))
                .thenReturn(java.util.Optional.empty()); // 本地用户无缓存，fallback 到 DB
        prepareLeadAssignment(2L, 1L);  // sales 用户=1 是 secondaryLead，不是 primaryLead
        assertThatThrownBy(() -> service.submitBid(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submitBid_initializesEvaluationRecord() {
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-Team")));
        prepareLeadAssignment(1L, 2L);
        service.submitBid(1L, 1L);
        verify(projectEvaluationRepository).save(argThat(e ->
                e.getProjectId().equals(1L)
                        && "IN_PROGRESS".equals(e.getSubStage())
                        && e.getEvaluationStartedAt() != null
                        && "".equals(e.getNotes())));
    }

    @Test
    void submitBid_approvedReview_incompleteTasks_denied_409() {
        prepareSubmitBidHappyPath();
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.TODO).build()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-Team")));
        prepareLeadAssignment(1L, 2L);

        assertThatThrownBy(() -> service.submitBid(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("仍有 1 个任务未完成", "无法提交投标");
                });
    }

    @Test
    void submitBid_approvedReview_todoTask_denied_409() {
        prepareSubmitBidHappyPath();
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.TODO).build()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-Team")));
        prepareLeadAssignment(1L, 2L);

        assertThatThrownBy(() -> service.submitBid(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("仍有 1 个任务未完成");
                });
    }

    @Test
    void submitBid_approvedReview_reviewStatusTask_denied_409() {
        prepareSubmitBidHappyPath();
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.REVIEW).build()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-Team")));
        prepareLeadAssignment(1L, 2L);

        assertThatThrownBy(() -> service.submitBid(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("仍有 1 个任务未完成");
                });
    }

    @Test
    void submitBid_approvedReview_missingBidDocument_denied_409() {
        prepareSubmitBidHappyPath();
        prepareNoBidDocument();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-Team")));
        prepareLeadAssignment(1L, 2L);

        assertThatThrownBy(() -> service.submitBid(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("尚未上传标书文件");
                });
    }

    @Test
    void submitBid_afterDraftingStage_isIdempotent() {
        prepareSubmitBidHappyPath();
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.TODO).build()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-Team")));
        prepareLeadAssignment(1L, 2L);
        when(projectStageService.currentStage(1L)).thenReturn(ProjectStage.EVALUATING);

        var view = service.submitBid(1L, 1L);

        assertThat(view).isNotNull();
        verify(projectStageService, never()).requestTransition(eq(1L), eq(ProjectStage.EVALUATING), any());
    }

    @Test
    void submitBid_approvedReview_allTasksCompleted_allowed() {
        prepareSubmitBidHappyPath();
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.COMPLETED).build(),
                Task.builder().id(2L).projectId(1L).title("b").status(Task.Status.COMPLETED).build()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-Team")));
        prepareLeadAssignment(1L, 2L);

        var view = service.submitBid(1L, 1L);

        assertThat(view).isNotNull();
        verify(projectStageService).requestTransition(
                eq(1L),
                eq(ProjectStage.EVALUATING),
                any());
        verify(notificationService).notifyStageTransition(
                eq(1L),
                eq(ProjectStage.DRAFTING),
                eq(ProjectStage.EVALUATING),
                eq(1L));
    }

    @Test
    void submitBid_bidAdmin_allowed() {
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "/bidAdmin")));
        var view = service.submitBid(1L, 1L);
        assertThat(view).isNotNull();
    }

    @Test
    void submitBid_bidLead_allowed() {
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-TeamLeader")));
        var view = service.submitBid(1L, 1L);
        assertThat(view).isNotNull();
    }

    @Test
    void submitBid_admin_allowed() {
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "admin")));
        // admin 路径权限判断不依赖 lead；prepareSubmitBidHappyPath 默认 lead=empty 也能通过
        var view = service.submitBid(1L, 1L);
        assertThat(view).isNotNull();
    }

    @Test
    void submitBid_bidSpecialist_asSecondaryLead_allowed() {
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-Team")));
        prepareLeadAssignment(2L, 1L);  // bid_specialist 用户=1 是 secondaryLead
        var view = service.submitBid(1L, 1L);
        assertThat(view).isNotNull();
    }

    @Test
    void submitBid_bidSpecialist_asPrimaryLead_allowed() {
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-Team")));
        prepareLeadAssignment(1L, 2L);  // bid_specialist 用户=1 是 primaryLead
        var view = service.submitBid(1L, 1L);
        assertThat(view).isNotNull();
    }

    @Test
    void submitBid_sales_notLead_denied_403() {
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-projectLeader")));
        prepareLeadAssignment(2L, 3L);  // sales 用户=1 既不是 primary 也不是 secondary
        assertThatThrownBy(() -> service.submitBid(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submitBid_sales_noLeadAssignment_denied_403() {
        prepareSubmitBidHappyPath();
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-projectLeader")));
        // prepareSubmitBidHappyPath 默认 lead=empty，sales 无 lead 分配应被拒绝
        assertThatThrownBy(() -> service.submitBid(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submitBid_adminStaff_denied_403() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-administration")));
        assertThatThrownBy(() -> service.submitBid(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submitBid_noRoleProfile_denied_403() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, null)));
        assertThatThrownBy(() -> service.submitBid(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── CO-346：submitForReview 服务层角色 + 闸门校验 ─────────────────────────

    @Test
    void submitForReview_unauthorizedRole_denied_403() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "bid-otherDept")));

        assertThatThrownBy(() -> service.submitForReview(1L, 99L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);

        verify(bidReviewAppService, never())
                .submitForReview(any(Long.class), any(Long.class), any(Long.class));
    }

    @Test
    void submitForReview_admin_delegatesToBidReviewAppService() {
        // admin 通过 assertCanSubmit；闸门校验通过后委托给 BidReviewAppService
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "admin")));
        lenient().when(taskRepository.findByProjectId(1L)).thenReturn(List.of());
        prepareBidDocument();
        org.mockito.Mockito.lenient().doNothing().when(bidReviewAppService)
                .submitForReview(any(Long.class), any(Long.class), any(Long.class));

        service.submitForReview(1L, 99L, 1L);

        verify(bidReviewAppService).submitForReview(1L, 99L, 1L);
    }

    @Test
    void submitForReview_incompleteTasks_denied_409() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "admin")));
        when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.TODO).build(),
                Task.builder().id(2L).projectId(1L).title("b").status(Task.Status.REVIEW).build()));

        assertThatThrownBy(() -> service.submitForReview(1L, 99L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("仍有 2 个任务未完成", "无法提交标书审核");
                });

        verify(bidReviewAppService, never())
                .submitForReview(any(Long.class), any(Long.class), any(Long.class));
    }

    @Test
    void submitForReview_missingBidDocument_denied_409() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser(1L, "admin")));
        lenient().when(taskRepository.findByProjectId(1L)).thenReturn(List.of(
                Task.builder().id(1L).projectId(1L).title("a").status(Task.Status.COMPLETED).build()));
        prepareNoBidDocument();

        assertThatThrownBy(() -> service.submitForReview(1L, 99L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("尚未上传标书文件", "无法提交标书审核");
                });

        verify(bidReviewAppService, never())
                .submitForReview(any(Long.class), any(Long.class), any(Long.class));
    }

    // ── CO-373：OSS 缓存角色优先于 DB roleProfile ─────────────────────────

    @Test
    void co373_ossUser_cacheHasSalesRole_dbRoleNull_asPrimaryLead_allowed() {
        prepareSubmitBidHappyPath();
        var user = mockOssUser(1L, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ossPermissionCache.getRoleCode("test-1"))
                .thenReturn(java.util.Optional.of("bid-projectLeader"));
        prepareLeadAssignment(1L, 2L);

        var view = service.submitBid(1L, 1L);

        assertThat(view).isNotNull();
    }

    @Test
    void co373_ossUser_cacheHasBidSpecialistRole_dbRoleNull_asSecondaryLead_allowed() {
        prepareSubmitBidHappyPath();
        var user = mockOssUser(1L, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ossPermissionCache.getRoleCode("test-1"))
                .thenReturn(java.util.Optional.of("bid-Team"));
        prepareLeadAssignment(2L, 1L);

        var view = service.submitBid(1L, 1L);

        assertThat(view).isNotNull();
    }

    @Test
    void co373_ossUser_cacheMiss_dbRoleNull_denied_403() {
        prepareSubmitBidHappyPath();
        var user = mockOssUser(1L, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ossPermissionCache.getRoleCode("test-1"))
                .thenReturn(java.util.Optional.empty());
        prepareLeadAssignment(1L, 2L);

        assertThatThrownBy(() -> service.submitBid(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void co373_localUser_dbHasSalesRole_cacheMiss_asPrimaryLead_allowed() {
        prepareSubmitBidHappyPath();
        var user = mockUser(1L, "bid-projectLeader");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ossPermissionCache.getRoleCode("test-1"))
                .thenReturn(java.util.Optional.empty());
        prepareLeadAssignment(1L, 2L);

        var view = service.submitBid(1L, 1L);

        assertThat(view).isNotNull();
    }

    @Test
    void co373_ossUser_cacheRoleTakesPrecedenceOverDbRole() {
        prepareSubmitBidHappyPath();
        var user = mockOssUser(1L, "bid-administration");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ossPermissionCache.getRoleCode("test-1"))
                .thenReturn(java.util.Optional.of("bid-projectLeader"));
        prepareLeadAssignment(1L, 2L);

        var view = service.submitBid(1L, 1L);

        assertThat(view).isNotNull();
    }
}
