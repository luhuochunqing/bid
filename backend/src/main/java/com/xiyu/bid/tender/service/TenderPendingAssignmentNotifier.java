// Input: Tender entity
// Output: 待分配通知 to all 投标管理员 + 投标组长 (通知中心 + 企微)
// Pos: Service/标讯待分配通知外壳

package com.xiyu.bid.tender.service;

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
 * 标讯待分配通知服务。
 * <p>标讯创建后，给所有投标管理员和投标组长发送待分配提醒（通知中心 + 企微）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenderPendingAssignmentNotifier {

    private static final String TASK_TITLE_PREFIX = "【待分配】";
    private static final String TASK_DESC_TEMPLATE = "标讯「%s」已创建，请及时分配项目负责人。";
    private static final Set<String> ASSIGNER_ROLES = Set.of("admin", "bid_admin", "bid_lead");

    private final NotificationApplicationService notificationService;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyPendingAssignment(Tender tender) {
        String title = TASK_TITLE_PREFIX + tender.getTitle();
        String desc = String.format(TASK_DESC_TEMPLATE, tender.getTitle());

        List<User> assigners = findAssigners();
        List<Long> assignerIds = assigners.stream().map(User::getId).collect(Collectors.toList());

        // 通知中心 + 企微（TODO 暂不创建，taskService 需要 Project 权限）
        if (!assignerIds.isEmpty()) {
            createNotificationSafely(
                    NotificationType.APPROVAL.name(),
                    "TENDER",
                    tender.getId(),
                    title,
                    desc,
                    assignerIds,
                    tender.getCreatorId() != null ? tender.getCreatorId() : 1L
            );
        }
    }

    private List<User> findAssigners() {
        try {
            return userRepository.findEnabledByRoleProfileCodes(ASSIGNER_ROLES);
        } catch (RuntimeException e) {
            log.error("Failed to query assigners: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void createNotificationSafely(String type, String sourceEntityType, Long sourceEntityId,
                                          String title, String body, List<Long> recipientIds, Long createdBy) {
        try {
            notificationService.createNotification(
                    new CreateNotificationRequest(
                            type, sourceEntityType, sourceEntityId,
                            title, body, Collections.emptyMap(), recipientIds
                    ),
                    createdBy
            );
        } catch (RuntimeException e) {
            log.error("Failed to send pending-assignment notification: {}", e.getMessage());
        }
    }
}
