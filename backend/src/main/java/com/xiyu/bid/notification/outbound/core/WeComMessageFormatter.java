// Input: notification type / source entity / platform base URL
// Output: FormattedMessage record used to build 企微 textcard payload
// Pos: Pure Core/企微推送消息格式化器
package com.xiyu.bid.notification.outbound.core;

/**
 * Pure formatter that assembles a WeCom textcard payload.
 *
 * <p>No Spring, no IO, no logging. Takes explicit inputs, returns a value.
 */
public final class WeComMessageFormatter {

    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 512;
    private static final String DEFAULT_BTN_TEXT = "查看详情";
    private static final String INBOX_PATH = "/inbox";

    private WeComMessageFormatter() {
    }

    public record FormattedMessage(String title, String description, String url, String btnText) {
    }

    /**
     * 格式化企微 textcard payload。
     *
     * <p>URL 解析优先级（P0-1 修复）：
     * <ol>
     *   <li>{@code payloadTargetUrl} 非空且以 "/" 开头 → 拼接 platformBaseUrl + payloadTargetUrl</li>
     *   <li>否则回退到 sourceEntityType → 路径映射</li>
     *   <li>映射失败 → 回退到 /inbox</li>
     * </ol>
     *
     * <p>引入 payloadTargetUrl 的根因：某些通知（如文档变更 DOCUMENT_CHANGE）的合理跳转目标
     * 不是 sourceEntityType 对应的实体页（如 /document/editor/），而是关联项目的子页面
     * （/project/{id}/drafting）。payload.targetUrl 由业务侧精确指定，应优先于默认映射。
     */
    public static FormattedMessage format(
        String notificationTitle,
        String notificationType,
        String sourceEntityType,
        Long sourceEntityId,
        String platformBaseUrl
    ) {
        return format(notificationTitle, notificationType, sourceEntityType, sourceEntityId, platformBaseUrl, null);
    }

    public static FormattedMessage format(
        String notificationTitle,
        String notificationType,
        String sourceEntityType,
        Long sourceEntityId,
        String platformBaseUrl,
        String payloadTargetUrl
    ) {
        String safeTitle = truncate(defaultIfBlank(notificationTitle, "新通知"), MAX_TITLE_LENGTH);
        String typeLabel = humanType(notificationType);
        String description = truncate(typeLabel + " · " + safeTitle, MAX_DESCRIPTION_LENGTH);
        String url = buildUrl(platformBaseUrl, sourceEntityType, sourceEntityId, payloadTargetUrl);
        return new FormattedMessage(safeTitle, description, url, DEFAULT_BTN_TEXT);
    }

    private static String buildUrl(String platformBaseUrl, String sourceEntityType, Long sourceEntityId, String payloadTargetUrl) {
        String base = defaultIfBlank(platformBaseUrl, "");
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        // P0-1：payload targetUrl 优先（业务侧精确指定的深链）
        if (payloadTargetUrl != null && payloadTargetUrl.startsWith("/")) {
            return normalizedBase + payloadTargetUrl;
        }
        String sourcePath = resolveSourcePath(sourceEntityType, sourceEntityId);
        if (sourcePath == null) {
            return normalizedBase + INBOX_PATH;
        }
        return normalizedBase + sourcePath;
    }

    private static String resolveSourcePath(String entityType, Long entityId) {
        if (entityType == null || entityId == null || entityId <= 0) {
            return null;
        }
        return switch (entityType.toUpperCase(java.util.Locale.ROOT)) {
            case "PROJECT" -> "/project/" + entityId;
            case "BIDDING", "TENDER" -> "/bidding/" + entityId;
            case "DOCUMENT" -> "/document/editor/" + entityId;
            case "WAREHOUSE_EXPIRY_WARNING", "WAREHOUSE_EXPIRED_WARNING", "WAREHOUSE" -> "/knowledge/warehouse?id=" + entityId;
            default -> null;
        };
    }

    private static String humanType(String type) {
        if (type == null || type.isBlank()) {
            return "通知";
        }
        return switch (type.toUpperCase(java.util.Locale.ROOT)) {
            case "MENTION" -> "提及";
            case "APPROVAL" -> "审批";
            case "DEADLINE" -> "截止";
            case "TASK_UPDATE" -> "任务";
            case "DOCUMENT_CHANGE" -> "文档变更";
            case "SYSTEM" -> "系统";
            default -> "通知";
        };
    }

    private static String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
