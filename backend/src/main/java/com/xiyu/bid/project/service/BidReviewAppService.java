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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 标书审核流程编排服务。提交审核 → 审核人收到通知 → 审核通过/驳回 → 状态持久化。
 * 核心规则委托给 {@link BidReviewPolicy}；入参校验委托给 {@link BidReviewReviewerValidator}。
 * CO-483 + CO-484 多人审核：最多 2 人，任一驳回即终态，全通过才整体 APPROVED。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BidReviewAppService {

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
     * 提交标书审核（CO-484 v2 多人审核）。
     * 校验：人数 1-3、去重、不含 submittedBy；必须含项目经理；不得选 primaryLead / 团队成员；辅助人员解禁。
     */
    @Auditable(action = "SUBMIT_BID_REVIEW", entityType = "BidDocumentReview",
            description = "提交标书审核", projectScoped = true)
    public void submitForReview(Long projectId, List<Long> reviewerIds, Long submittedBy) {
        // 入参校验
        BidReviewReviewerValidator.validateReviewerIds(reviewerIds, submittedBy);

        Optional<BidDocumentReviewEntity> existing = reviewRepository.findByProjectId(projectId);
        BidReviewStatus currentStatus = existing.map(e -> parseStatus(e.getStatus())).orElse(null);
        var decision = BidReviewPolicy.canSubmitReview(currentStatus);
        if (!decision.allowed()) {
            throw toResponseStatus(decision);
        }

        // CO-484 v2：审核人组成校验（含项目经理、排除 primaryLead/团队成员、辅助人员解禁）
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在"));
        ProjectLeadAssignment lead = leadAssignmentRepository.findByProjectId(projectId).orElse(null);
        BidReviewReviewerValidator.validateReviewerComposition(reviewerIds, project, lead, submittedBy);

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
        // XIYU-Q：并发提交可能导致重复插入，用 INSERT IGNORE 依赖 uk_review_reviewer 唯一键兜底
        assignmentRepository.deleteByReviewId(review.getId());
        for (Long rid : reviewerIds) {
            assignmentRepository.insertIgnore(review.getId(), rid);
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
            description = "标书审核通过", projectScoped = true)
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
        mine.setComment(comment);
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
            description = "标书审核驳回", projectScoped = true)
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

        // 任一驳回即终态：直接设置 REJECTED，不走 computeAggregateStatus（聚合规则的"任一 REJECTED"分支由此落地）
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

    /** 读取审核状态（CO-484 多人审核，只读事务）。 */
    @Transactional(readOnly = true)
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

    /** 审核状态快照（CO-484 多人审核）。 */
    public record ReviewState(
            String status,
            Long reviewerId,
            String rejectReason,
            String reviewerName,
            List<ReviewerDecisionDto> reviewers
    ) {
    }
}
