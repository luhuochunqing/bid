package com.xiyu.bid.casework.application;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
class CasePrecipitationNotifier {

    private final NotificationApplicationService notificationApplicationService;

    void notifySuccess(Long projectId, String projectName, int count, long durationMs, Project project, Long triggerUserId) {
        try {
            Long recipientId = resolveRecipient(project, triggerUserId);
            if (recipientId == null) {
                log.info("No recipient for project {} (triggerUserId={}, managerId={}), skip case precipitation notification",
                        projectId, triggerUserId, project != null ? project.getManagerId() : null);
                return;
            }
            String duration = formatDuration(durationMs);
            String title = "AI 生成案例完成";
            String body = "项目【" + projectName + "】的 AI 生成案例已完成，入库 "
                    + count + " 条案例。处理时长 " + duration + "。";
            Map<String, Object> payload = Map.of(
                    "projectId", projectId, "count", count,
                    "success", true, "durationMs", durationMs,
                    "deeplink", "/knowledge/case");
            notificationApplicationService.createNotification(
                    new CreateNotificationRequest("SYSTEM", "PROJECT", projectId, title, body, payload,
                            List.of(recipientId)), recipientId);
            log.info("Case precipitation success notification sent to user {} for project {} (triggerUserId={})",
                    recipientId, projectId, triggerUserId);
        } catch (RuntimeException e) {
            log.error("Failed to send case precipitation success notification for project {}", projectId, e);
        }
    }

    void notifyFailure(Long projectId, String projectName, String reason, Project project, Long triggerUserId) {
        try {
            Long recipientId = resolveRecipient(project, triggerUserId);
            if (recipientId == null) {
                log.info("No recipient for project {} (triggerUserId={}, managerId={}), skip case precipitation notification",
                        projectId, triggerUserId, project != null ? project.getManagerId() : null);
                return;
            }
            String title = "AI 生成案例失败";
            String body = "项目【" + projectName + "】的 AI 生成案例执行失败。失败原因：" + reason;
            Map<String, Object> payload = Map.of(
                    "projectId", projectId, "count", 0,
                    "success", false, "reason", reason,
                    "deeplink", "/knowledge/case");
            notificationApplicationService.createNotification(
                    new CreateNotificationRequest("SYSTEM", "PROJECT", projectId, title, body, payload,
                            List.of(recipientId)), recipientId);
            log.info("Case precipitation failure notification sent to user {} for project {} (triggerUserId={})",
                    recipientId, projectId, triggerUserId);
        } catch (RuntimeException e) {
            log.error("Failed to send case precipitation failure notification for project {}", projectId, e);
        }
    }

    /**
     * 解析收件人：优先用 triggerUserId（手动触发路径会带），fallback 用 project.managerId。
     */
    private Long resolveRecipient(Project project, Long triggerUserId) {
        if (triggerUserId != null) {
            return triggerUserId;
        }
        return project != null ? project.getManagerId() : null;
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) return ms + " 毫秒";
        if (ms < 60000) return (ms / 1000) + " 秒";
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        return minutes + " 分 " + seconds + " 秒";
    }
}
