// Input: 项目 id、审核人 id 列表、当前用户、驳回原因
// Output: ProjectDraftingViewDto；纯编排，核心规则委托给 BidReviewPolicy
// Pos: project/service/ - 编排层，负责标书审核流程的编排与通知
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.project.core.BidReviewPolicy;
import com.xiyu.bid.project.core.BidReviewStatus;
import com.xiyu.bid.project.dto.ReviewerDecisionDto;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 标书审核流程编排服务。
 * <p>职责：提交审核 → 审核人收到通知 → 审核通过/驳回 → 状态持久化。</p>
 * <p>核心规则委托给 {@link BidReviewPolicy}。</p>
 *
 * <p>CO-483 + CO-484 多人审核（2026-07-03）：</p>
 * <ul>
 *   <li>submitForReview 接收 List<Long> reviewerIds（最多 2 人），为每人建未决 assignment</li>
 *   <li>approve/reject 记录当前审核人的个人决策 → 聚合判断整体状态</li>
 *   <li>CO-483：驳回重提时清空旧 assignment；reviewerIds 不得含 submittedBy/primaryLead/secondaryLead</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BidReviewAppService {

    /** CO-484 调整后需求：审核人最多 2 人。 */
    private static final int MAX_REVIEWERS = 2;

    private final BidDocumentReviewRepository reviewRepository;
    private final BidReviewAssignmentRepository assignmentRepository;
    private final ProjectLeadAssignmentRepository leadAssignmentRepository;
    private final UserRepository userRepository;
    private final TenderRepository tenderRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final ProjectNotificationService projectNotificationService;

    /**
     * 提交标书审核（CO-484 多人审核）。
     * <p>创建/更新审核记录为 REVIEWING 状态，为每个 reviewerId 建未决 assignment，并发起代办通知给所有审核人。</p>
     *
     * <p>校验：</p>
     * <ul>
     *   <li>人数 1-2、去重</li>
     *   <li>不含 submittedBy（CO-483 后端兜底）</li>
     *   <li>不含项目经理 / 团队成员 / primaryLead / secondaryLead（CO-483 排除范围）</li>
     * </ul>
     */
    @Auditable(action = "SUBMIT_BID_REVIEW", entityType = "BidDocumentReview",
            description = "提交标书审核")
    public void submitForReview(Long projectId, List<Long> reviewerIds, Long submittedBy) {
        // 入参校验
        validateReviewerIds(reviewerIds, submittedBy, projectId);

        Optional<BidDocumentReviewEntity> existing = reviewRepository.findByProjectId(projectId);
        BidReviewStatus currentStatus = existing.map(e -> parseStatus(e.getStatus())).orElse(null);
        var decision = BidReviewPolicy.canSubmitReview(currentStatus);
        if (!decision.allowed()) {
            throw toResponseStatus(decision);
        }

        // 校验审核人是否参与了本项目（每个 reviewerId 都校验）
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在"));
        validateReviewersNotProjectParticipants(reviewerIds, project, projectId);

        BidDocumentReviewEntity review = existing.orElseGet(() -> BidDocumentReviewEntity.builder()
                .projectId(projectId).build());
        // 保留 reviewer_id 作主审核人（第一个），兼容历史查询
        review.setReviewerId(reviewerIds.get(0));
        review.setSubmittedBy(submittedBy);
        review.setStatus(BidReviewStatus.REVIEWING.name());
        review.setRejectReason(null);
        review.setReviewedAt(null);
        reviewRepository.save(review);

        // CO-483：驳回重提场景清空旧 assignment，避免残留决策
        assignmentRepository.deleteByReviewId(review.getId());
        for (Long rid : reviewerIds) {
            assignmentRepository.save(BidReviewAssignmentEntity.builder()
                    .reviewId(review.getId())
                    .reviewerId(rid)
                    .build());
        }

        // 审核人加入项目成员（VIEWER），确保能看到项目列表
        for (Long rid : reviewerIds) {
            projectMemberRepository.findByProjectIdAndUserId(projectId, rid)
                    .orElseGet(() -> projectMemberRepository.save(ProjectMember.builder()
                            .projectId(projectId)
                            .userId(rid)
                            .permissionLevel("VIEWER")
                            .build()));
        }

        // 通知所有审核人
        for (Long rid : reviewerIds) {
            sendBidReviewNotification(projectId, rid, submittedBy);
        }
        log.info("Bid submitted for review project={} reviewers={} by={}", projectId, reviewerIds, submittedBy);
    }

    /**
     * 审核通过（CO-484 多人审核）。
     * <p>记录当前审核人的 APPROVED 决策 → 聚合判断 → 全通过才整体 APPROVED。</p>
     */
    @Auditable(action = "APPROVE_BID", entityType = "BidDocumentReview",
            description = "标书审核通过")
    public void approveBid(Long projectId, Long currentUserId, String comment) {
        BidDocumentReviewEntity review = reviewRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到标书审核记录"));
        List<BidReviewAssignmentEntity> assignments = assignmentRepository
                .findByReviewIdOrderByCreatedAtAsc(review.getId());
        List<Long> reviewerIds = assignments.stream().map(BidReviewAssignmentEntity::getReviewerId).toList();

        var decision = BidReviewPolicy.canApprove(
                parseStatus(review.getStatus()),
                review.getSubmittedBy(),
                reviewerIds,
                currentUserId);
        if (!decision.allowed()) {
            throw toResponseStatus(decision);
        }

        // 记录当前审核人的 APPROVED 决策
        BidReviewAssignmentEntity mine = assignments.stream()
                .filter(a -> Objects.equals(a.getReviewerId(), currentUserId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "仅指派的审核人可以审批"));
        mine.setDecision("APPROVED");
        mine.setDecidedAt(LocalDateTime.now());
        assignmentRepository.save(mine);

        // 聚合判断整体状态
        List<String> decisions = assignmentRepository.findByReviewIdOrderByCreatedAtAsc(review.getId())
                .stream().map(BidReviewAssignmentEntity::getDecision).toList();
        BidReviewStatus aggregate = BidReviewPolicy.computeAggregateStatus(decisions);
        if (aggregate == BidReviewStatus.APPROVED) {
            review.setStatus(BidReviewStatus.APPROVED.name());
            review.setReviewedAt(LocalDateTime.now());
            reviewRepository.save(review);
            projectNotificationService.notifyBidReviewResult(projectId, review.getSubmittedBy(), true, currentUserId);
        }
        log.info("Bid approved project={} by={} aggregate={} comment={}", projectId, currentUserId, aggregate, comment);
    }

    /**
     * 驳回（CO-484 多人审核）。
     * <p>记录当前审核人的 REJECTED 决策 + reason → 任一驳回即整体 REJECTED。</p>
     */
    @Auditable(action = "REJECT_BID", entityType = "BidDocumentReview",
            description = "标书审核驳回")
    public void rejectBid(Long projectId, Long currentUserId, String reason) {
        BidDocumentReviewEntity review = reviewRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到标书审核记录"));
        List<BidReviewAssignmentEntity> assignments = assignmentRepository
                .findByReviewIdOrderByCreatedAtAsc(review.getId());
        List<Long> reviewerIds = assignments.stream().map(BidReviewAssignmentEntity::getReviewerId).toList();

        var decision = BidReviewPolicy.canReject(
                parseStatus(review.getStatus()),
                reason,
                review.getSubmittedBy(),
                reviewerIds,
                currentUserId);
        if (!decision.allowed()) {
            throw toResponseStatus(decision);
        }

        // 记录当前审核人的 REJECTED 决策 + reason
        BidReviewAssignmentEntity mine = assignments.stream()
                .filter(a -> Objects.equals(a.getReviewerId(), currentUserId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "仅指派的审核人可以驳回"));
        mine.setDecision("REJECTED");
        mine.setComment(reason);
        mine.setDecidedAt(LocalDateTime.now());
        assignmentRepository.save(mine);

        // 任一驳回即整体 REJECTED（聚合规则保证）
        review.setStatus(BidReviewStatus.REJECTED.name());
        // 拼接"驳回人：原因"列表
        List<BidReviewAssignmentEntity> latest = assignmentRepository.findByReviewIdOrderByCreatedAtAsc(review.getId());
        String rejectReasonText = buildRejectReasonText(latest);
        review.setRejectReason(rejectReasonText);
        review.setReviewedAt(LocalDateTime.now());
        reviewRepository.save(review);

        projectNotificationService.notifyBidReviewResult(projectId, review.getSubmittedBy(), false, currentUserId);
        log.info("Bid rejected project={} by={} reason={}", projectId, currentUserId, reason);
    }

    /**
     * 读取审核状态（CO-484 多人审核）。
     */
    public ReviewState getReviewState(Long projectId) {
        Optional<BidDocumentReviewEntity> reviewOpt = reviewRepository.findByProjectId(projectId);
        if (reviewOpt.isEmpty()) {
            return new ReviewState(null, null, null, null, List.of());
        }
        BidDocumentReviewEntity review = reviewOpt.get();
        List<BidReviewAssignmentEntity> assignments = assignmentRepository
                .findByReviewIdOrderByCreatedAtAsc(review.getId());
        List<ReviewerDecisionDto> reviewers = assignments.stream()
                .map(a -> ReviewerDecisionDto.builder()
                        .reviewerId(a.getReviewerId())
                        .reviewerName(resolveUserName(a.getReviewerId()))
                        .decision(a.getDecision())
                        .comment(a.getComment())
                        .build())
                .toList();
        return new ReviewState(
                review.getStatus(),
                review.getReviewerId(),
                review.getRejectReason(),
                resolveUserName(review.getReviewerId()),
                reviewers
        );
    }

    // -- 辅助方法 ----------------------------------------------------------

    /**
     * CO-484 入参校验：人数 1-2、去重、不含 submittedBy。
     */
    private void validateReviewerIds(List<Long> reviewerIds, Long submittedBy, Long projectId) {
        if (reviewerIds == null || reviewerIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标书审核人不能为空");
        }
        // 去重
        Set<Long> deduped = new LinkedHashSet<>(reviewerIds);
        if (deduped.size() != reviewerIds.size()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标书审核人不能重复");
        }
        if (deduped.size() > MAX_REVIEWERS) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "标书审核人最多 " + MAX_REVIEWERS + " 人");
        }
        // CO-483 后端兜底：不能选自己
        if (submittedBy != null && deduped.contains(submittedBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标书审核人不能选择自己");
        }
    }

    /**
     * CO-483 排除范围校验：审核人不得是项目经理 / 团队成员 / primaryLead / secondaryLead。
     */
    private void validateReviewersNotProjectParticipants(List<Long> reviewerIds, Project project, Long projectId) {
        Set<Long> participantIds = new LinkedHashSet<>();
        if (project.getManagerId() != null) participantIds.add(project.getManagerId());
        if (project.getTeamMembers() != null) participantIds.addAll(project.getTeamMembers());

        // CO-483 追加排除：primaryLead / secondaryLead
        ProjectLeadAssignment lead = leadAssignmentRepository.findByProjectId(projectId).orElse(null);
        if (lead != null) {
            if (lead.getPrimaryLeadUserId() != null) participantIds.add(lead.getPrimaryLeadUserId());
            if (lead.getSecondaryLeadUserId() != null) participantIds.add(lead.getSecondaryLeadUserId());
        }

        for (Long rid : reviewerIds) {
            if (participantIds.contains(rid)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "标书审核人必须是未参与本项目的人员（含投标负责人/辅助人员）");
            }
        }
    }

    private String buildRejectReasonText(List<BidReviewAssignmentEntity> assignments) {
        List<String> parts = new ArrayList<>();
        for (BidReviewAssignmentEntity a : assignments) {
            if ("REJECTED".equals(a.getDecision()) && a.getComment() != null && !a.getComment().isBlank()) {
                String name = resolveUserName(a.getReviewerId());
                parts.add((name != null ? name : "审核人") + "：" + a.getComment());
            }
        }
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private ResponseStatusException toResponseStatus(BidReviewPolicy.Decision decision) {
        HttpStatus status = decision.cause() == BidReviewPolicy.Decision.Cause.IDENTITY
                ? HttpStatus.FORBIDDEN
                : HttpStatus.CONFLICT;
        return new ResponseStatusException(status, decision.reason());
    }

    private void sendBidReviewNotification(Long projectId, Long reviewerId, Long submittedBy) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return;
        Tender tender = tenderRepository.findById(project.getTenderId()).orElse(null);

        String tenderTitle = tender != null ? tender.getTitle() : null;
        String bidOpeningTime = tender != null && tender.getBidOpeningTime() != null
                ? tender.getBidOpeningTime().toString() : null;
        String purchaserName = tender != null ? tender.getPurchaserName() : null;
        String submitterName = userRepository.findById(submittedBy)
                .map(User::getFullName).orElse(null);

        projectNotificationService.notifyBidReviewSubmitted(
                projectId, reviewerId, submittedBy,
                tenderTitle, bidOpeningTime, purchaserName, submitterName);
    }

    private static BidReviewStatus parseStatus(String raw) {
        if (raw == null) return null;
        try {
            return BidReviewStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse(null);
    }

    /**
     * 审核状态快照（CO-484 多人审核）。
     */
    public record ReviewState(
            String status,
            Long reviewerId,
            String rejectReason,
            String reviewerName,
            List<ReviewerDecisionDto> reviewers
    ) {
    }
}
