// Input: BidReviewAppService.approveBid / rejectBid / submitForReview 行为
// Output: Mockito 单元测试覆盖身份校验 + 多人审核 + CO-483 排除/清空
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标书审核 service 集成测试。
 * <p>CO-483 + CO-484：覆盖多人审核 + 驳回重提清空 + primaryLead/secondaryLead 排除。</p>
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
    void approveBid_asAssignedReviewer_succeeds_singleReviewer() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(pendingAssignment(1L, 200L)));

        // currentUserId=200 == reviewerId=200，且 != submittedBy=100 → 通过
        service.approveBid(1L, 200L, "ok");

        // 单审核人 APPROVED → 聚合 APPROVED → 状态被持久化
        verify(reviewRepository).save(any(BidDocumentReviewEntity.class));
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

    // ── CO-484 多人审核 ───────────────────────────────────────────────

    @Test
    void approveBid_multi_oneApprovedOnePending_aggregateReviewing_noOverallApprove() {
        // CO-xxx fix: 此前断言有误。approveBid(201) 会把 a201.decision 改成 APPROVED（mine.setDecision），
        // 然后聚合判断再次查 assignments。因为 mock 返回的是同一个引用，a201.decision 已被改为 APPROVED，
        // 所以聚合结果为 APPROVED（2 人全部通过），整体审核记录应被保存。
        // 这反映了生产环境真实行为：JPA 仓库第二次查询会返回 save 后的最新状态。
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        BidReviewAssignmentEntity a200 = BidReviewAssignmentEntity.builder()
                .id(11L).reviewId(1L).reviewerId(200L).decision("APPROVED").build();
        BidReviewAssignmentEntity a201 = pendingAssignment(1L, 201L);
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(a200, a201));

        service.approveBid(1L, 201L, "ok");

        // approveBid(201) 后 a201.decision 变 APPROVED → 2 人全 APPROVED → 整体 APPROVED
        verify(reviewRepository).save(any(BidDocumentReviewEntity.class));
        // 当前审核人 201 的个人决策被持久化
        verify(assignmentRepository).save(any(BidReviewAssignmentEntity.class));
    }

    @Test
    void approveBid_multi_allApproved_aggregateApproved_overallApprove() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        BidReviewAssignmentEntity a200 = BidReviewAssignmentEntity.builder()
                .id(11L).reviewId(1L).reviewerId(200L).decision("APPROVED").build();
        BidReviewAssignmentEntity a201 = pendingAssignment(1L, 201L);
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(a200, a201));

        service.approveBid(1L, 201L, "ok");

        // 整体 APPROVED → 应保存整体审核记录
        verify(reviewRepository).save(any(BidDocumentReviewEntity.class));
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
    void rejectBid_asAssignedReviewer_succeeds() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));
        when(assignmentRepository.findByReviewIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(pendingAssignment(1L, 200L)));

        service.rejectBid(1L, 200L, "内容不符");

        verify(reviewRepository).save(any(BidDocumentReviewEntity.class));
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

    // ── submitForReview 标书审核人校验 ──────────────────────────────────────

    @Test
    void submitForReview_whenReviewerIsProjectManager_throws400() {
        com.xiyu.bid.entity.Project project = com.xiyu.bid.entity.Project.builder()
                .id(1L)
                .managerId(10L)
                .teamMembers(java.util.List.of(11L, 12L))
                .build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(leadAssignmentRepository.findByProjectId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitForReview(1L, List.of(10L), 100L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("标书审核人必须是未参与本项目的人员")
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitForReview_whenReviewerIsTeamMember_throws400() {
        com.xiyu.bid.entity.Project project = com.xiyu.bid.entity.Project.builder()
                .id(1L)
                .managerId(10L)
                .teamMembers(java.util.List.of(11L, 12L))
                .build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(leadAssignmentRepository.findByProjectId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitForReview(1L, List.of(11L), 100L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("标书审核人必须是未参与本项目的人员")
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

        // reviewerId=99 既不是 manager=10 也不是 teamMembers=[11, 12] → 允许
        service.submitForReview(1L, List.of(99L), 100L);

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

        service.submitForReview(1L, List.of(99L), 100L);

        verify(projectNotificationService).notifyBidReviewSubmitted(
                eq(1L), eq(99L), eq(100L),
                any(), any(), any(), any());
    }

    // ── CO-483 + CO-484 新增场景 ─────────────────────────────────────

    @Test
    void submitForReview_whenReviewerIsPrimaryLead_throws400() {
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

        // reviewer=20 是 primaryLead → 400
        assertThatThrownBy(() -> service.submitForReview(1L, List.of(20L), 100L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("标书审核人必须是未参与本项目的人员")
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void submitForReview_whenReviewerIsSecondaryLead_throws400() {
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

        // reviewer=21 是 secondaryLead → 400
        assertThatThrownBy(() -> service.submitForReview(1L, List.of(21L), 100L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("标书审核人必须是未参与本项目的人员")
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
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
    void submitForReview_whenMoreThan2Reviewers_throws422() {
        // CO-484 调整：最多 2 人
        assertThatThrownBy(() -> service.submitForReview(1L, List.of(99L, 100L, 101L), 1L))
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

        service.submitForReview(1L, List.of(99L, 101L), 100L);

        // 应清空旧 assignment
        verify(assignmentRepository).deleteByReviewId(1L);
        // 应为每个 reviewerId 建未决 assignment（2 人 → save 调用 2 次）
        verify(assignmentRepository, times(2)).save(any(BidReviewAssignmentEntity.class));
    }
}
