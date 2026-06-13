// Input: 项目 id、审核人 id、当前用户、驳回原因
// Output: ProjectDraftingViewDto；纯编排，核心规则委托给 BidReviewPolicy
// Pos: project/service/ - 编排层，负责标书审核流程的编排与通知
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.project.core.BidReviewPolicy;
import com.xiyu.bid.project.core.BidReviewStatus;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 标书审核流程编排服务。
 * <p>职责：提交审核 → 审核人收到通知 → 审核通过/驳回 → 状态持久化。</p>
 * <p>核心规则委托给 {@link BidReviewPolicy}。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BidReviewAppService {

    private final BidDocumentReviewRepository reviewRepository;
    private final NotificationApplicationService notificationService;
    private final UserRepository userRepository;
    private final TenderRepository tenderRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    /**
     * 提交标书审核。
     * 创建/更新审核记录为 REVIEWING 状态，并发起代办通知给审核人。
     */
    @Auditable(action = "SUBMIT_BID_REVIEW", entityType = "BidDocumentReview",
            description = "提交标书审核")
    public void submitForReview(Long projectId, Long reviewerId, Long submittedBy) {
        Optional<BidDocumentReviewEntity> existing = reviewRepository.findByProjectId(projectId);
        BidReviewStatus currentStatus = existing.map(e -> parseStatus(e.getStatus())).orElse(null);
        var decision = BidReviewPolicy.canSubmitReview(currentStatus);
        if (!decision.allowed()) {
            throw toResponseStatus(decision);
        }

        // 校验审核人是否参与了本项目（标书审核人必须是未参与本项目的人员）
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在"));
        if (reviewerId != null) {
            if (reviewerId.equals(project.getManagerId()) || 
                (project.getTeamMembers() != null && project.getTeamMembers().contains(reviewerId))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标书审核人必须是未参与本项目的人员");
            }
        }

        BidDocumentReviewEntity review = existing.orElseGet(() -> BidDocumentReviewEntity.builder()
                .projectId(projectId).build());
        review.setReviewerId(reviewerId);
        review.setSubmittedBy(submittedBy);
        review.setStatus(BidReviewStatus.REVIEWING.name());
        review.setRejectReason(null);
        review.setReviewedAt(null);
        reviewRepository.save(review);

        // 审核人加入项目成员，确保能看到项目列表
        if (reviewerId != null) {
            projectMemberRepository.findByProjectIdAndUserId(projectId, reviewerId)
                    .orElseGet(() -> projectMemberRepository.save(ProjectMember.builder()
                            .projectId(projectId)
                            .userId(reviewerId)
                            .permissionLevel("VIEWER")
                            .build()));
        }

        sendBidReviewNotification(projectId, reviewerId, submittedBy);
        log.info("Bid submitted for review project={} reviewer={} by={}", projectId, reviewerId, submittedBy);
    }

    /**
     * 审核通过。
     *
     * <p>身份校验（IJSTZG 根因修复 2026-06-07）：仅指派的审核人可执行审批，
     * 且提交人不能审批自己提交的标书。校验在 {@link BidReviewPolicy#canApprove}
     * 集中实现；HTTP 状态码由 {@link #toResponseStatus} 根据拒绝原因映射：</p>
     * <ul>
     *   <li>{@code STATE} → 409 Conflict（资源已处于目标状态）</li>
     *   <li>{@code IDENTITY} → 403 Forbidden（无权限/自我审批/非指派人）</li>
     * </ul>
     */
    @Auditable(action = "APPROVE_BID", entityType = "BidDocumentReview",
            description = "标书审核通过")
    public void approveBid(Long projectId, Long currentUserId, String comment) {
        BidDocumentReviewEntity review = reviewRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到标书审核记录"));
        var decision = BidReviewPolicy.canApprove(
                parseStatus(review.getStatus()),
                review.getSubmittedBy(),
                review.getReviewerId(),
                currentUserId);
        if (!decision.allowed()) {
            throw toResponseStatus(decision);
        }

        review.setStatus(BidReviewStatus.APPROVED.name());
        review.setReviewedAt(LocalDateTime.now());
        reviewRepository.save(review);

        log.info("Bid approved project={} by={} comment={}", projectId, currentUserId, comment);
    }

    /**
     * 驳回。
     *
     * <p>身份校验同 {@link #approveBid}：仅指派的审核人可驳回，且不能驳回自己提交的标书。</p>
     */
    @Auditable(action = "REJECT_BID", entityType = "BidDocumentReview",
            description = "标书审核驳回")
    public void rejectBid(Long projectId, Long currentUserId, String reason) {
        BidDocumentReviewEntity review = reviewRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到标书审核记录"));
        var decision = BidReviewPolicy.canReject(
                parseStatus(review.getStatus()),
                reason,
                review.getSubmittedBy(),
                review.getReviewerId(),
                currentUserId);
        if (!decision.allowed()) {
            throw toResponseStatus(decision);
        }

        review.setStatus(BidReviewStatus.REJECTED.name());
        review.setRejectReason(reason);
        review.setReviewedAt(LocalDateTime.now());
        reviewRepository.save(review);

        log.info("Bid rejected project={} by={} reason={}", projectId, currentUserId, reason);
    }

    /**
     * 读取审核状态。
     */
    public ReviewState getReviewState(Long projectId) {
        Optional<BidDocumentReviewEntity> review = reviewRepository.findByProjectId(projectId);
        return new ReviewState(
                review.map(BidDocumentReviewEntity::getStatus).orElse(null),
                review.map(BidDocumentReviewEntity::getReviewerId).orElse(null),
                review.map(BidDocumentReviewEntity::getRejectReason).orElse(null),
                resolveUserName(review.map(BidDocumentReviewEntity::getReviewerId).orElse(null))
        );
    }

    // -- 辅助方法 ----------------------------------------------------------

    /**
     * 将 {@link BidReviewPolicy.Decision} 拒绝原因映射为 HTTP 状态异常。
     *
     * <p>映射规则（IJSTZG 根因修复）：</p>
     * <ul>
     *   <li>{@code STATE} → 409 Conflict（资源已处于不允许操作的终态）</li>
     *   <li>{@code IDENTITY} → 403 Forbidden（自审/非指派人/无身份）</li>
     * </ul>
     *
     * <p>此前所有拒绝原因一律返回 409，会让"前端误显→用户点击→后端 409"暴露为
     * "操作冲突" 误导排查；拆分为 403 让身份问题直接可识别。</p>
     */
    private ResponseStatusException toResponseStatus(BidReviewPolicy.Decision decision) {
        HttpStatus status = decision.cause() == BidReviewPolicy.Decision.Cause.IDENTITY
                ? HttpStatus.FORBIDDEN
                : HttpStatus.CONFLICT;
        return new ResponseStatusException(status, decision.reason());
    }

    private void sendBidReviewNotification(Long projectId, Long reviewerId, Long submittedBy) {
        // 通知失败不影响审核记录提交；日志记录异常但不抛出
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) {
                log.warn("Project {} not found, skip notification", projectId);
                return;
            }
            Tender tender = tenderRepository.findById(project.getTenderId()).orElse(null);

            String projectName = project.getName();
            String tenderTitle = tender != null ? tender.getTitle() : "";
            String bidOpeningTime = tender != null && tender.getBidOpeningTime() != null
                    ? tender.getBidOpeningTime().toString() : "";
            String purchaserName = tender != null ? tender.getPurchaserName() : "";

            // 查提交人姓名
            String submitterName = userRepository.findById(submittedBy)
                    .map(User::getFullName).orElse("");

            notificationService.createNotification(new CreateNotificationRequest(
                    NotificationType.BID_REVIEW.name(),
                    "Project",
                    projectId,
                    "标书审核：您有一个标书待审核 - " + projectName,
                    String.format(
                            "项目名称：%s\n招标主体：%s\n开标时间：%s\n提交人：%s\n\n请前往标书制作页面查看投标文件并完成审核。",
                            projectName, purchaserName, bidOpeningTime, submitterName),
                    Map.of(
                            "projectId", String.valueOf(projectId),
                            "projectName", projectName,
                            "tenderTitle", tenderTitle,
                            "bidOpeningTime", bidOpeningTime,
                            "purchaserName", purchaserName,
                            "submitterName", submitterName,
                            "targetUrl", "/project/" + projectId + "/drafting"),
                    List.of(reviewerId)
            ), submittedBy);
            log.info("Bid review notification sent to reviewer={} for project={}", reviewerId, projectId);
        } catch (RuntimeException e) {
            log.warn("Bid review notification failed for project={} reviewer={}: {}",
                    projectId, reviewerId, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
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
     * 审核状态快照。
     */
    public record ReviewState(
            String status,
            Long reviewerId,
            String rejectReason,
            String reviewerName
    ) {
    }
}
