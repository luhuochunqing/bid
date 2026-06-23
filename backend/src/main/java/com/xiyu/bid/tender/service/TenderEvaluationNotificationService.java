// Input: Tender entity and evaluator User
// Output: 通知中心消息（审核人审批通知 + 创建人状态变更提醒）
// Pos: Service/标讯评估通知外壳
// 维护声明: REQ-BC-010 通知逻辑统一在此，TenderEvaluationSubmissionService.submit() 委托本类执行.
// 2026-06 更新: 审核通知 → 投标管理员+投标组长(通知中心+企微)；状态变更提醒 → 创建人(仅通知中心).

package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 标讯评估提交后通知服务（REQ-BC-010）。
 * <p>提交后：
 * <ul>
 *   <li>投标管理员 + 投标组长 → 通知中心（审批通知，自动推企微）</li>
 *   <li>标讯创建人 → 仅通知中心（状态变更提醒，无审核权限）</li>
 * </ul>
 * <p>创建失败时记录错误日志，不阻塞主流程。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenderEvaluationNotificationService {

    private static final String REVIEW_TASK_TITLE_PREFIX = "【评估待审】";
    private static final String REVIEW_TASK_DESC_TEMPLATE = "标讯「%s」已完成评估提交，请及时审核并决定是否投标。";
    private static final String STATUS_CHANGE_TITLE_TEMPLATE = "标讯「%s」状态已变更为已评估";
    private static final String STATUS_CHANGE_BODY_TEMPLATE = "标讯「%s」已由项目负责人提交评估，等待投标管理员/组长审核。";
    private static final Set<String> REVIEWER_ROLES = RoleProfileCatalog.GLOBAL_ACCESS_ROLES;

    private final NotificationApplicationService notificationService;
    private final UserRepository userRepository;

    /**
     * 评估提交后发送通知。
     * <p>审核通知：给所有投标管理员和投标组长发通知中心消息（自动推企微）。
     * <p>状态提醒：仅通知中心给标讯创建人。
     * <p>使用独立事务，防止通知失败影响主标讯流程。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createEvaluationNotifications(Tender tender) {
        String reviewTitle = REVIEW_TASK_TITLE_PREFIX + tender.getTitle();
        String reviewDesc = String.format(REVIEW_TASK_DESC_TEMPLATE, tender.getTitle());
        // 1) 查找所有审核人（投标管理员 + 投标组长）
        List<User> reviewers = findReviewers();
        List<Long> reviewerIds = reviewers.stream().map(User::getId).collect(Collectors.toList());

        // 2) 给审核人发通知中心消息（自动触发企微推送）
        if (!reviewerIds.isEmpty()) {
            createNotificationSafely(
                    NotificationType.APPROVAL.name(),
                    "TENDER",
                    tender.getId(),
                    reviewTitle,
                    reviewDesc,
                    reviewerIds,
                    tender.getCreatorId() != null ? tender.getCreatorId() : 1L
            );
        }

        // 3) 给标讯创建人发状态变更提醒（仅通知中心）
        Long creatorId = tender.getCreatorId();
        if (creatorId != null && !reviewerIds.contains(creatorId)) {
            String statusTitle = String.format(STATUS_CHANGE_TITLE_TEMPLATE, tender.getTitle());
            String statusBody = String.format(STATUS_CHANGE_BODY_TEMPLATE, tender.getTitle());
            createNotificationSafely(
                    NotificationType.INFO.name(),
                    "TENDER",
                    tender.getId(),
                    statusTitle,
                    statusBody,
                    Collections.singletonList(creatorId),
                    creatorId
            );
        }
    }

    // ---------- helpers ----------

    private List<User> findReviewers() {
        try {
            return userRepository.findEnabledByRoleProfileCodes(REVIEWER_ROLES);
        } catch (RuntimeException e) {
            log.error("Failed to query reviewers by roles {}: {}", REVIEWER_ROLES, e.getMessage());
            return Collections.emptyList();
        }
    }

    private void createNotificationSafely(String type, String sourceEntityType, Long sourceEntityId,
                                          String title, String body, List<Long> recipientIds, Long createdBy) {
        try {
            notificationService.createNotification(
                    new CreateNotificationRequest(
                            type,
                            sourceEntityType,
                            sourceEntityId,
                            title,
                            body,
                            Collections.emptyMap(),
                            recipientIds
                    ),
                    createdBy
            );
            log.debug("Notification sent: type={}, recipients={}, title={}", type, recipientIds.size(), title);
        } catch (RuntimeException e) {
            log.error("Failed to send notification (type={}, recipients={}): {}",
                    type, recipientIds.size(), e.getMessage());
        }
    }
}
