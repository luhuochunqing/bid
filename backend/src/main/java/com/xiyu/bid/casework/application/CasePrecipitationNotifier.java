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

    void notifySuccess(Long projectId, String projectName, int count, long durationMs, Project project) {
        try {
            Long recipientId = project != null ? project.getManagerId() : null;
            if (recipientId == null) {
                log.info("No managerId for project {}, skip case precipitation notification", projectId);
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
            log.info("Case precipitation success notification sent to user {} for project {}", recipientId, projectId);
        } catch (RuntimeException e) {
            log.error("Failed to send case precipitation success notification for project {}", projectId, e);
        }
    }

    void notifyFailure(Long projectId, String projectName, String reason, Project project) {
        try {
            Long recipientId = project != null ? project.getManagerId() : null;
            if (recipientId == null) {
                log.info("No managerId for project {}, skip case precipitation notification", projectId);
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
            log.info("Case precipitation failure notification sent to user {} for project {}", recipientId, projectId);
        } catch (RuntimeException e) {
            log.error("Failed to send case precipitation failure notification for project {}", projectId, e);
        }
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) return ms + " 毫秒";
        if (ms < 60000) return (ms / 1000) + " 秒";
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        return minutes + " 分 " + seconds + " 秒";
    }
}
