// Input: project, tender, targetUrl
// Output: 待立项通知消息值对象
// Pos: Pure Core/待立项通知消息模板策略
package com.xiyu.bid.notification.core;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 投标立项后「待立项」通知消息模板策略 —— 纯核心工厂。
 *
 * <p>从 {@link NotificationMessagePolicy} 拆分出来，避免原类超过 300 行。</p>
 */
public final class PendingInitiationNotificationMessagePolicy {

    private static final String ENTITY_PROJECT = "PROJECT";

    private PendingInitiationNotificationMessagePolicy() {
    }

    /**
     * 投标立项后待立项通知。
     * <p>文案：{@code 待立项 - {projectName}} / {@code 【{tenderName}】已投标，项目「{projectName}」待立项，请尽快处理。}
     */
    public static NotificationMessagePolicy.NotificationMessage forPendingInitiation(
            final Project project, final Tender tender, final String targetUrl) {
        Long projectId = project == null ? null : project.getId();
        String projectName = project == null ? "" : nullToEmpty(project.getName());
        Long tenderId = tender == null ? null : tender.getId();
        String tenderName = tender == null ? "" : nullToEmpty(tender.getTitle());
        Map<String, Object> payload = basePayload(projectId, projectName, targetUrl);
        payload.put("tenderId", tenderId);
        payload.put("tenderName", tenderName);
        return new NotificationMessagePolicy.NotificationMessage(
                NotificationType.PENDING_INITIATION.name(), ENTITY_PROJECT, projectId,
                "待立项 - " + projectName,
                "【" + tenderName + "】已投标，项目「" + projectName + "」待立项，请尽快处理。", payload);
    }

    private static Map<String, Object> basePayload(
            final Long projectId, final String projectName, final String targetUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("projectName", projectName);
        payload.put("targetUrl", targetUrl);
        return payload;
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }
}
