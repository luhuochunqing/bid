// Input: tenderId, projectId, tenderName, projectName, ownerUserId, triggeredByUserId
// Output: 创建待立项站内通知（失败降级，不阻塞主流程）
// Pos: notification/application/ - 投标立项通知应用服务编排层
package com.xiyu.bid.notification.application;

import com.xiyu.bid.notification.core.BidNotificationPolicy;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.entity.Notification;
import com.xiyu.bid.notification.repository.NotificationRepository;
import com.xiyu.bid.notification.service.NotificationApplicationService;
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

/**
 * 投标立项通知应用服务。
 *
 * <p>职责仅限于编排：查询历史通知用于去重 → 调用纯核心策略生成请求 →
 * 调用 {@link NotificationApplicationService#createNotification}。任何步骤失败都捕获并降级，
 * 不向上抛异常，避免阻塞投标主流程。</p>
 *
 * <p>通知创建在独立事务（REQUIRES_NEW）中执行，与主业务事务隔离。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BidNotificationApplicationService {

    private static final String SOURCE_ENTITY_TYPE = "PROJECT";
    private static final String TITLE_PREFIX = "待立项 - ";
    private static final int DEDUP_WINDOW_MINUTES = 5;

    private final NotificationApplicationService notificationApplicationService;
    private final NotificationRepository notificationRepository;

    /**
     * 发送「待立项」站内通知给项目负责人。
     *
     * <p>Spec acceptance scenario 明确：即使项目负责人与当前操作用户为同一人，也按规则发送通知，
     * 因此本方法不主动排除 {@code triggeredByUserId}。</p>
     *
     * @param tenderId          标讯 ID
     * @param projectId         项目 ID
     * @param tenderName        标讯名称（由调用方显式传入，避免本服务查询数据库）
     * @param projectName       项目名称（由调用方显式传入）
     * @param ownerUserId       项目负责人用户 ID（由调用方 TenderEvaluationService 显式传入；
     *                          项目刚创建时 ProjectInitiationDetails 尚未生成，不能通过 recipientPolicy 解析）
     * @param triggeredByUserId 触发人用户 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendPendingInitiationNotification(
            Long tenderId, Long projectId, String tenderName, String projectName,
            Long ownerUserId, Long triggeredByUserId) {
        try {
            if (ownerUserId == null) {
                log.warn("No PROJECT_OWNER provided for pending initiation notification, projectId={}", projectId);
                return;
            }

            List<Instant> existingTimestamps = loadExistingTimestamps(projectId, TITLE_PREFIX);

            Optional<CreateNotificationRequest> request = BidNotificationPolicy.createRequest(
                    tenderId,
                    projectId,
                    tenderName,
                    projectName,
                    targetUrl(projectId),
                    triggeredByUserId,
                    List.of(ownerUserId),
                    Instant.now(),
                    existingTimestamps);
            if (request.isEmpty()) {
                log.info("Pending initiation notification skipped due to dedup, projectId={}", projectId);
                return;
            }

            notificationApplicationService.createNotification(request.get(), triggeredByUserId);
        } catch (RuntimeException ex) {
            log.error("Failed to send pending initiation notification for tenderId={}, projectId={}",
                    tenderId, projectId, ex);
        }
    }

    private List<Instant> loadExistingTimestamps(Long projectId, String titlePrefix) {
        Instant now = Instant.now();
        LocalDateTime windowStart = LocalDateTime.ofInstant(now, ZoneOffset.UTC).minusMinutes(DEDUP_WINDOW_MINUTES);
        List<Notification> existing = notificationRepository
                .findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter(
                        SOURCE_ENTITY_TYPE,
                        projectId,
                        NotificationType.PENDING_INITIATION.name(),
                        windowStart);
        return existing.stream()
                .filter(notification -> notification.getTitle() != null
                        && notification.getTitle().startsWith(titlePrefix))
                .map(Notification::getCreatedAt)
                .filter(createdAt -> createdAt != null)
                .map(createdAt -> createdAt.toInstant(ZoneOffset.UTC))
                .toList();
    }

    private static String targetUrl(Long projectId) {
        return "/project/" + projectId + "/initiation";
    }
}
