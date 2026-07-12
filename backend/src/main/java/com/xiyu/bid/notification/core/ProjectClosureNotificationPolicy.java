// Input: projectId, projectName, targetUrl, triggeredByUserId + dedup context
// Output: Optional<CreateNotificationRequest> for pending closure application notification
// Pos: Pure Core/项目结项待申请通知策略
package com.xiyu.bid.notification.core;

import com.xiyu.bid.notification.core.NotificationMessagePolicy.NotificationMessage;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 待结项申请通知策略 —— 纯核心。
 *
 * <p>负责把项目结项触发事件转换为站内通知请求，并基于显式传入的时间戳列表做去重决策。
 * 不依赖 Spring、Repository、系统时间或任何 IO。</p>
 */
public final class ProjectClosureNotificationPolicy {

    private ProjectClosureNotificationPolicy() {
    }

    /**
     * 生成待结项申请通知请求。
     *
     * @param projectId          项目 ID
     * @param projectName        项目名称
     * @param targetUrl          通知跳转链接
     * @param triggeredByUserId  触发人用户 ID
     * @param now                当前时间（显式传入，避免隐式系统依赖）
     * @param existingTimestamps 同一业务动作已创建通知的时间戳列表
     * @param recipientUserIds   接收人用户 ID 列表
     * @return 去重通过时返回通知请求；窗口内已存在通知时返回 empty
     */
    public static Optional<CreateNotificationRequest> createRequest(
            Long projectId,
            String projectName,
            String targetUrl,
            Long triggeredByUserId,
            Instant now,
            List<Instant> existingTimestamps,
            List<Long> recipientUserIds) {
        if (now == null) {
            return Optional.empty();
        }
        if (NotificationDedupPolicy.isDuplicate(now, existingTimestamps)) {
            return Optional.empty();
        }
        String safeProjectName = projectName == null ? "" : projectName;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("projectName", safeProjectName);
        payload.put("targetUrl", targetUrl);
        NotificationMessage message = new NotificationMessage(
                NotificationType.PENDING_CLOSURE_APPLICATION.name(),
                "PROJECT",
                projectId,
                "待结项申请 - " + safeProjectName,
                "【" + safeProjectName + "】已进入结项阶段，请尽快提交结项申请。",
                payload);
        CreateNotificationRequest request = new CreateNotificationRequest(
                message.type(),
                message.sourceEntityType(),
                message.sourceEntityId(),
                message.title(),
                message.body(),
                message.payload(),
                recipientUserIds);
        return Optional.of(request);
    }
}
