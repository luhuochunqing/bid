// Input: BidReviewAppService.approveBid / rejectBid / submitForReview / getReviewState 行为
// Output: Mockito 单元测试覆盖身份校验 + 状态机校验 + 多人审核聚合 + CO-483 排除/清空
// Pos: backend test source — 防止 PR #281 类型的反复 bug 复活
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.project.core.BidReviewStatus;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.entity.BidReviewAssignmentEntity;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.notification.ProjectNotificationService;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.project.repository.BidReviewAssignmentRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标书审核 service 集成测试。
 * <p>CO-484 v2（2026-07-04）：多人审核上限 3 人、必须含项目经理、辅助人员解禁、驳回重提清空。</p>
 * <p>测试覆盖维度：</p>
 * <ul>
 *   <li>身份校验：自审/非指派人/无身份 → 403</li>
 *   <li>状态机校验：REVIEWING/APPROVED/REJECTED → 各操作是否允许</li>
 *   <li>审核人组成：缺项目经理 → 400；含 primaryLead/团队成员 → 400；含 secondaryLead → 允许</li>
 *   <li>多人审核聚合：1 通过 1 未决 → REVIEWING；2 通过 → APPROVED；任一驳回 → REJECTED</li>
 *   <li>ArgumentCaptor 状态断言：save 的是 APPROVED/REJECTED 而非其他</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BidReviewAppServiceTest {

    @Mock BidDocumentReviewRepository reviewRepository;
    @Mock BidReviewAssignmentRepository assignmentRepository;
    @Mock ProjectLeadAssignmentRepository leadAssignmentRepository;
    @Mock UserRepository userRepository;
    @Mock TenderRepository tenderRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock ProjectAccessScopeService projectAccessScopeService;
    @Mock ProjectNotificationService projectNotificationService;

    BidReviewAppService service;

    @BeforeEach
    void setUp() {
        service = new BidReviewAppService(
                reviewRepository,
                assignmentRepository,
                leadAssignmentRepository,
                userRepository,
                tenderRepository,
                projectRepository,
                projectMemberRepository,
                projectAccessScopeService,
                projectNotificationService);
        lenient().when(reviewRepository.save(any(BidDocumentReviewEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(assignmentRepository.save(any(BidReviewAssignmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(projectMemberRepository.findByProjectIdAndUserId(any(), any()))
                .thenReturn(Optional.empty());
    }

    /** 构造一个 REVIEWING 状态的审核记录，submitter=100, reviewer=200 */
    private BidDocumentReviewEntity reviewing(long submitter, long reviewer) {
        return BidDocumentReviewEntity.builder()
                .id(1L)
                .projectId(1L)
                .reviewerId(reviewer)
                .submittedBy(submitter)
                .status(BidReviewStatus.REVIEWING.name())
                .build();
    }

    /** 构造未决 assignment */
    private BidReviewAssignmentEntity pendingAssignment(long reviewId, long reviewerId) {
        return BidReviewAssignmentEntity.builder()
                .id(reviewId * 100 + reviewerId)
                .reviewId(reviewId)
                .reviewerId(reviewerId)
                .build();
    }

    // ── approveBid 身份校验（IJSTZG 根因修复 2026-06-07）──────────────

    @Test
    void approveBid_whenSelfSubmitted_throws403() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(pendingAssignment(1L, 200L)));

        // submittedBy (100) == currentUserId (100) → 自我审批 → 403
        assertThatThrownBy(() -> service.approveBid(1L, 100L, ""))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);

        // 关键断言：状态没有被修改
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void approveBid_whenWrongReviewer_throws403() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(pendingAssignment(1L, 200L)));

        // currentUserId=999 既不是 submitter 也不是 reviewer → 403
        assertThatThrownBy(() -> service.approveBid(1L, 999L, ""))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void approveBid_asAssignedReviewer_succeeds_singleReviewer_savesApprovedStatus() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(pendingAssignment(1L, 200L)));

        // currentUserId=200 == reviewerId=200，且 != submittedBy=100 → 通过
        service.approveBid(1L, 200L, "ok");

        // 单审核人 APPROVED → 聚合 APPROVED → 状态被持久化
        // ArgumentCaptor 加固：验证 save 的确实是 APPROVED 状态，而非其他
        ArgumentCaptor<BidDocumentReviewEntity> captor = ArgumentCaptor.forClass(BidDocumentReviewEntity.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void approveBid_whenNullCurrentUserId_throws403() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(pendingAssignment(1L, 200L)));

        assertThatThrownBy(() -> service.approveBid(1L, null, ""))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void approveBid_alreadyApproved_throws409_not403() {
        BidDocumentReviewEntity approved = BidDocumentReviewEntity.builder()
                .id(1L).projectId(1L).reviewerId(200L).submittedBy(100L)
                .status(BidReviewStatus.APPROVED.name())
                .build();
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.approveBid(1L, 200L, ""))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    // ── approveBid 状态机校验（新增）──────────────────────────────────

    @Test
    void approveBid_whenRejectedStatus_throws409() {
        // 状态机校验：REJECTED 状态下不能再 approveBid
        BidDocumentReviewEntity rejected = BidDocumentReviewEntity.builder()
                .id(1L).projectId(1L).reviewerId(200L).submittedBy(100L)
                .status(BidReviewStatus.REJECTED.name())
                .build();
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.approveBid(1L, 200L, "ok"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);

        verify(reviewRepository, never()).save(any());
    }

    // ── CO-484 多人审核 ───────────────────────────────────────────────

    @Test
    void approveBid_multi_oneApprovedOnePending_doesNotSaveReview() {
        // 场景：a200 已 APPROVED，a201 未决
        // approveBid(201) 会把 a201.decision 设为 APPROVED 并 save assignment
        // 但聚合查询看到的是 a201.decision=null（模拟数据库未刷新/并发场景/聚合前未持久化）
        // 聚合 [APPROVED, null] → REVIEWING → 不整体 APPROVE → reviewRepository.save 不被调用
        // 此测试守护"聚合判断在 REVIEWING 时不 save review"的分支
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        // 用 thenAnswer 每次返回新对象，避免 mock 副作用掩盖聚合分支
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenAnswer(inv -> List.of(
                        BidReviewAssignmentEntity.builder()
                                .id(11L).reviewId(1L).reviewerId(200L).decision("APPROVED").build(),
                        BidReviewAssignmentEntity.builder()
                                .id(12L).reviewId(1L).reviewerId(201L).build()  // decision=null
                ));

        service.approveBid(1L, 201L, "ok");

        // 聚合 REVIEWING → 不整体 APPROVE → reviewRepository.save 不被调用
        verify(reviewRepository, never()).save(any());
        // 但当前审核人的决策被持久化
        verify(assignmentRepository).save(any(BidReviewAssignmentEntity.class));
    }

    @Test
    void approveBid_multi_secondApproveAggregatesToApproved_savesReviewWithApprovedStatus() {
        // 场景：a200 已 APPROVED，a201 未决
        // approveBid(201) 后 a201.decision 变 APPROVED → 聚合 [APPROVED, APPROVED] → APPROVED
        // 模拟 JPA 第二次查询返回 save 后的最新状态（同一引用，setDecision 副作用可见）
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        BidReviewAssignmentEntity a200 = BidReviewAssignmentEntity.builder()
                .id(11L).reviewId(1L).reviewerId(200L).decision("APPROVED").build();
        BidReviewAssignmentEntity a201 = pendingAssignment(1L, 201L);
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(a200, a201));

        service.approveBid(1L, 201L, "ok");

        // approveBid(201) 后 a201.decision 变 APPROVED → 2 人全 APPROVED → 整体 APPROVED
        // ArgumentCaptor 加固：验证 save 的确实是 APPROVED 状态
        ArgumentCaptor<BidDocumentReviewEntity> captor = ArgumentCaptor.forClass(BidDocumentReviewEntity.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("APPROVED");
        verify(assignmentRepository).save(any(BidReviewAssignmentEntity.class));
    }

    // ── rejectBid 身份校验 ───────────────────────────────────────────

    @Test
    void rejectBid_whenSelfSubmitted_throws403() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(pendingAssignment(1L, 200L)));

        assertThatThrownBy(() -> service.rejectBid(1L, 100L, "内容不符"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectBid_asAssignedReviewer_succeeds_savesRejectedStatus() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(pendingAssignment(1L, 200L)));

        service.rejectBid(1L, 200L, "内容不符");

        // ArgumentCaptor 加固：验证 save 的确实是 REJECTED 状态
        ArgumentCaptor<BidDocumentReviewEntity> captor = ArgumentCaptor.forClass(BidDocumentReviewEntity.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void rejectBid_emptyReason_throws409() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(pendingAssignment(1L, 200L)));

        assertThatThrownBy(() -> service.rejectBid(1L, 200L, ""))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    // ── rejectBid 状态机校验（新增）──────────────────────────────────

    @Test
    void rejectBid_whenApprovedStatus_throws409() {
        // 状态机校验：APPROVED 状态下不能再 rejectBid
        BidDocumentReviewEntity approved = BidDocumentReviewEntity.builder()
                .id(1L).projectId(1L).reviewerId(200L).submittedBy(100L)
                .status(BidReviewStatus.APPROVED.name())
                .build();
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.rejectBid(1L, 200L, "内容不符"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);

        verify(reviewRepository, never()).save(any());
    }

    // ── getReviewState 行为 ──────────────────────────────────────────

    @Test
    void getReviewState_returnsPersistedFields() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(pendingAssignment(1L, 200L)));

        var state = service.getReviewState(1L);
        assertThat(state.status()).isEqualTo("REVIEWING");
        assertThat(state.reviewerId()).isEqualTo(200L);
        assertThat(state.reviewers()).hasSize(1);
        assertThat(state.reviewers().get(0).getReviewerId()).isEqualTo(200L);
    }

    @Test
    void getReviewState_whenNoReview_returnsEmptyState() {
        // 覆盖 getReviewState 的空分支：无 review 记录时返回空 ReviewState
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.empty());

        var state = service.getReviewState(1L);

        assertThat(state.status()).isNull();
        assertThat(state.reviewerId()).isNull();
        assertThat(state.rejectReason()).isNull();
        assertThat(state.reviewerName()).isNull();
        assertThat(state.reviewers()).isEmpty();
    }

    // ── submitForReview 状态机校验（新增）────────────────────────────

    @Test
    void submitForReview_whenReviewing_throws409() {
        // 状态机校验：REVIEWING 状态下不能重复提交
        BidDocumentReviewEntity reviewing = BidDocumentReviewEntity.builder()
                .id(1L).projectId(1L).reviewerId(200L).submittedBy(100L)
                .status(BidReviewStatus.REVIEWING.name())
                .build();
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.of(reviewing));

        assertThatThrownBy(() -> service.submitForReview(1L, List.of(99L), 100L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitForReview_whenApproved_throws409() {
        // 状态机校验：APPROVED 状态下不能再提交审核
        BidDocumentReviewEntity approved = BidDocumentReviewEntity.builder()
                .id(1L).projectId(1L).reviewerId(200L).submittedBy(100L)
                .status(BidReviewStatus.APPROVED.name())
                .build();
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.submitForReview(1L, List.of(99L), 100L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);

        verify(reviewRepository, never()).save(any());
    }

    // ── submitForReview 标书审核人校验 ──────────────────────────────────────

    @Test
    void submitForReview_whenReviewersMissingManager_throws400() {
        // CO-484 v2：审核人必须包含项目经理（managerId=10），reviewer=99 不含 → 400
        com.xiyu.bid.entity.Project project = com.xiyu.bid.entity.Project.builder()
                .id(1L)
                .managerId(10L)
                .teamMembers(java.util.List.of(11L, 12L))
                .build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(leadAssignmentRepository.findByProjectId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitForReview(1L, List.of(99L), 100L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("需包含项目负责人")
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitForReview_whenReviewerIsTeamMember_throws400() {
        // CO-484 v2：含项目经理 10 + 团队成员 11 → 仍因团队成员被排除
        com.xiyu.bid.entity.Project project = com.xiyu.bid.entity.Project.builder()
                .id(1L)
                .managerId(10L)
                .teamMembers(java.util.List.of(11L, 12L))
                .build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(leadAssignmentRepository.findByProjectId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitForReview(1L, List.of(10L, 11L), 100L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不能选择投标负责人或项目团队成员")
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitForReview_whenReviewerIsExternal_succeeds() {
        com.xiyu.bid.entity.Project project = com.xiyu.bid.entity.Project.builder()
                .id(1L)
                .managerId(10L)
                .teamMembers(java.util.List.of(11L, 12L))
                .tenderId(1L)
                .build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(leadAssignmentRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        lenient().when(tenderRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(userRepository.findById(any())).thenReturn(Optional.empty());

        // CO-484 v2：reviewer=[10(manager), 99(外部)] → 含 manager、99 非排除项 → 允许
        service.submitForReview(1L, List.of(10L, 99L), 100L);

        verify(reviewRepository).save(any(BidDocumentReviewEntity.class));
    }

    @Test
    @DisplayName("submitForReview delegates notification to ProjectNotificationService (CO-439 fix)")
    void submitForReview_delegatesToProjectNotificationService() {
        com.xiyu.bid.entity.Project project = com.xiyu.bid.entity.Project.builder()
                .id(1L)
                .managerId(10L)
                .teamMembers(java.util.List.of(11L, 12L))
                .tenderId(1L)
                .name("测试项目")
                .build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(leadAssignmentRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        lenient().when(tenderRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(userRepository.findById(any())).thenReturn(Optional.empty());

        service.submitForReview(1L, List.of(10L, 99L), 100L);

        verify(projectNotificationService).notifyBidReviewSubmitted(
                eq(1L), eq(99L), eq(100L),
                any(), any(), any(), any());
    }

    // ── CO-483 + CO-484 新增场景 ─────────────────────────────────────

    @Test
    void submitForReview_whenReviewerIsPrimaryLead_throws400() {
        // CO-484 v2：reviewer=[10(manager), 20(primaryLead)] → 含 manager 但 primaryLead 被排除
        com.xiyu.bid.entity.Project project = com.xiyu.bid.entity.Project.builder()
                .id(1L)
                .managerId(10L)
                .teamMembers(new ArrayList<>())
                .tenderId(1L)
                .build();
        ProjectLeadAssignment lead = ProjectLeadAssignment.builder()
                .projectId(1L).primaryLeadUserId(20L).secondaryLeadUserId(21L).build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(leadAssignmentRepository.findByProjectId(1L)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> service.submitForReview(1L, List.of(10L, 20L), 100L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不能选择投标负责人或项目团队成员")
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitForReview_whenReviewerIsSecondaryLead_succeeds() {
        // CO-484 v2：投标辅助人员（secondaryLead）解禁，可作审核人。
        // reviewer=[10(manager), 21(secondaryLead)] → 含 manager、21 解禁 → 允许
        com.xiyu.bid.entity.Project project = com.xiyu.bid.entity.Project.builder()
                .id(1L)
                .managerId(10L)
                .teamMembers(new ArrayList<>())
                .tenderId(1L)
                .build();
        ProjectLeadAssignment lead = ProjectLeadAssignment.builder()
                .projectId(1L).primaryLeadUserId(20L).secondaryLeadUserId(21L).build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(leadAssignmentRepository.findByProjectId(1L)).thenReturn(Optional.of(lead));
        lenient().when(tenderRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(userRepository.findById(any())).thenReturn(Optional.empty());

        service.submitForReview(1L, List.of(10L, 21L), 100L);

        verify(reviewRepository).save(any(BidDocumentReviewEntity.class));
    }

    @Test
    void submitForReview_whenReviewerContainsSelf_throws400() {
        // CO-483 后端兜底：reviewerIds 含 submittedBy → 400
        assertThatThrownBy(() -> service.submitForReview(1L, List.of(100L, 99L), 100L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("标书审核人不能选择自己")
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void submitForReview_whenMoreThan3Reviewers_throws422() {
        // CO-484 v2：最多 3 人，4 人 → 422
        assertThatThrownBy(() -> service.submitForReview(1L, List.of(10L, 99L, 100L, 101L), 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("最多")
                .extracting("statusCode").isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void submitForReview_whenDuplicateReviewers_throws422() {
        assertThatThrownBy(() -> service.submitForReview(1L, List.of(99L, 99L), 100L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("不能重复")
                .extracting("statusCode").isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void submitForReview_whenRejectedResubmit_clearsOldAssignments() {
        // CO-483：驳回重提场景清空旧 assignment
        BidDocumentReviewEntity existing = BidDocumentReviewEntity.builder()
                .id(1L).projectId(1L).reviewerId(200L).submittedBy(100L)
                .status(BidReviewStatus.REJECTED.name())
                .build();
        com.xiyu.bid.entity.Project project = com.xiyu.bid.entity.Project.builder()
                .id(1L).managerId(10L).teamMembers(new ArrayList<>()).tenderId(1L).build();
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.of(existing));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(leadAssignmentRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        lenient().when(tenderRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(userRepository.findById(any())).thenReturn(Optional.empty());

        // CO-484 v2：reviewer 需含 manager=10，[10, 99] 满足组成校验
        service.submitForReview(1L, List.of(10L, 99L), 100L);

        // 应清空旧 assignment
        verify(assignmentRepository).deleteByReviewId(1L);
        // 应为每个 reviewerId 建未决 assignment（2 人 → save 调用 2 次）
        verify(assignmentRepository, times(2)).save(any(BidReviewAssignmentEntity.class));
    }
}
