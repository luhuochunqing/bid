// Input: BidReviewAppService.approveBid / rejectBid 行为
// Output: Mockito 单元测试覆盖身份校验分支（自审/非指派/合法/null user）
// Pos: backend test source — 防止 PR #281 类型的反复 bug 复活
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.project.core.BidReviewStatus;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标书审核 service 集成测试。
 * <p>覆盖 {@link BidReviewAppService#approveBid} / {@link BidReviewAppService#rejectBid}
 * 在身份校验分支下的行为：自审 403 / 非指派人 403 / 合法指派人 200 / null userId 403。</p>
 */
@ExtendWith(MockitoExtension.class)
class BidReviewAppServiceTest {

    @Mock BidDocumentReviewRepository reviewRepository;
    @Mock NotificationApplicationService notificationService;
    @Mock UserRepository userRepository;
    @Mock TenderRepository tenderRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock ProjectAccessScopeService projectAccessScopeService;

    BidReviewAppService service;

    @BeforeEach
    void setUp() {
        service = new BidReviewAppService(
                reviewRepository,
                notificationService,
                userRepository,
                tenderRepository,
                projectRepository,
                projectMemberRepository,
                projectAccessScopeService);
        lenient().when(reviewRepository.save(any(BidDocumentReviewEntity.class)))
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

    // ── approveBid 身份校验（IJSTZG 根因修复 2026-06-07）──────────────

    @Test
    void approveBid_whenSelfSubmitted_throws403() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));

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

        // currentUserId=999 既不是 submitter 也不是 reviewer → 403
        assertThatThrownBy(() -> service.approveBid(1L, 999L, ""))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void approveBid_asAssignedReviewer_succeeds() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));

        // currentUserId=200 == reviewerId=200，且 != submittedBy=100 → 通过
        service.approveBid(1L, 200L, "ok");

        // 验证状态被持久化为 APPROVED
        verify(reviewRepository).save(any(BidDocumentReviewEntity.class));
    }

    @Test
    void approveBid_whenNullCurrentUserId_throws403() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));

        assertThatThrownBy(() -> service.approveBid(1L, null, ""))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void approveBid_alreadyApproved_throws409_not403() {
        // 已通过状态属于资源状态机违规，应当返回 409 Conflict（不是身份问题）
        BidDocumentReviewEntity approved = BidDocumentReviewEntity.builder()
                .id(1L).projectId(1L).reviewerId(200L).submittedBy(100L)
                .status(BidReviewStatus.APPROVED.name())
                .build();
        when(reviewRepository.findByProjectId(1L)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.approveBid(1L, 200L, ""))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    // ── rejectBid 身份校验 ───────────────────────────────────────────

    @Test
    void rejectBid_whenSelfSubmitted_throws403() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));

        assertThatThrownBy(() -> service.rejectBid(1L, 100L, "内容不符"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectBid_asAssignedReviewer_succeeds() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));

        service.rejectBid(1L, 200L, "内容不符");

        verify(reviewRepository).save(any(BidDocumentReviewEntity.class));
    }

    @Test
    void rejectBid_emptyReason_throws409() {
        // 状态合法 (REVIEWING) + 身份合法 + reason 为空 → 状态机违规 → 409
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));

        assertThatThrownBy(() -> service.rejectBid(1L, 200L, ""))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    // ── getReviewState 行为 ──────────────────────────────────────────

    @Test
    void getReviewState_returnsPersistedFields() {
        when(reviewRepository.findByProjectId(1L))
                .thenReturn(Optional.of(reviewing(100L, 200L)));

        var state = service.getReviewState(1L);
        assertThat(state.status()).isEqualTo("REVIEWING");
        assertThat(state.reviewerId()).isEqualTo(200L);
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

        assertThatThrownBy(() -> service.submitForReview(1L, 10L, 100L))
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

        assertThatThrownBy(() -> service.submitForReview(1L, 11L, 100L))
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
        lenient().when(tenderRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(userRepository.findById(any())).thenReturn(Optional.empty());

        // reviewerId=99 既不是 manager=10 也不是 teamMembers=[11, 12] → 允许
        service.submitForReview(1L, 99L, 100L);

        verify(reviewRepository).save(any(BidDocumentReviewEntity.class));
    }
}
