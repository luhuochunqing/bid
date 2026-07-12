// Input: projectId, projectName, triggeredByUserId
// Output: creates a pending-closure-application notification for PROJECT_OWNER
// Pos: notification/application/ - orchestration layer only
package com.xiyu.bid.notification.application;

import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.core.ProjectClosureNotificationPolicy;
import com.xiyu.bid.notification.core.ProjectNotificationRole;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.entity.Notification;
import com.xiyu.bid.notification.repository.NotificationRepository;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.ProjectNotificationRecipientPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 项目结项待申请通知应用服务 —— 编排层。
 *
 * <p>职责：解析项目负责人、查询去重时间戳、调用纯核心策略、创建站内通知。
 * 失败时降级记录日志，不阻塞主流程。</p>
 *
 * <p>通知创建在独立事务（REQUIRES_NEW）中执行，与主业务事务隔离。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectClosureNotificationApplicationService {

    private static final String SOURCE_ENTITY_TYPE = "PROJECT";
    private static final String TITLE_PREFIX = "待结项申请 - ";
    private static final int DEDUP_WINDOW_MINUTES = 5;

    private final NotificationApplicationService notificationApplicationService;
    private final ProjectNotificationRecipientPolicy recipientPolicy;
    private final NotificationRepository notificationRepository;

    /**
     * 发送待结项申请通知给项目负责人。
     *
     * <p>Spec acceptance scenario 要求：项目负责人与当前操作用户为同一人时仍按规则发送，
     * 因此本方法不主动排除自己。</p>
     *
     * @param projectId     项目 ID
     * @param projectName   项目名称（由调用方显式传入，避免本服务查询数据库）
     * @param triggeredByUserId 触发人用户 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendPendingClosureApplicationNotification(Long projectId, String projectName, Long triggeredByUserId) {
        try {
            List<Long> recipients = recipientPolicy.resolveRecipients(
                    projectId,
                    Set.of(ProjectNotificationRole.PROJECT_OWNER),
                    null);
            if (recipients == null || recipients.isEmpty()) {
                log.debug("No PROJECT_OWNER recipient for pending closure notification, projectId={}", projectId);
                return;
            }

            List<Instant> existingTimestamps = loadExistingTimestamps(projectId, TITLE_PREFIX);
            Instant now = Instant.now();
            String targetUrl = "/project/" + projectId + "/closure";

            Optional<CreateNotificationRequest> requestOpt = ProjectClosureNotificationPolicy.createRequest(
                    projectId,
                    projectName,
                    targetUrl,
                    triggeredByUserId,
                    now,
                    existingTimestamps,
                    recipients);

            requestOpt.ifPresent(request ->
                    notificationApplicationService.createNotification(request, triggeredByUserId));
        } catch (RuntimeException e) {
            log.warn("sendPendingClosureApplicationNotification failed for project={}: {}",
                    projectId, e.getMessage(), e);
        }
    }

    private List<Instant> loadExistingTimestamps(Long projectId, String titlePrefix) {
        Instant now = Instant.now();
        LocalDateTime windowStart = LocalDateTime.ofInstant(now, ZoneOffset.UTC).minusMinutes(DEDUP_WINDOW_MINUTES);
        List<Notification> existing = notificationRepository
                .findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter(
                        SOURCE_ENTITY_TYPE,
                        projectId,
                        NotificationType.PENDING_CLOSURE_APPLICATION.name(),
                        windowStart);
        return existing.stream()
                .filter(notification -> notification.getTitle() != null
                        && notification.getTitle().startsWith(titlePrefix))
                .map(Notification::getCreatedAt)
                .filter(createdAt -> createdAt != null)
                .map(createdAt -> createdAt.toInstant(ZoneOffset.UTC))
                .toList();
    }
}