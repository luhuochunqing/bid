package com.xiyu.bid.alerts.domain;

import com.xiyu.bid.alerts.entity.AlertRule;

import java.util.Map;

/**
 * 告警消息构建纯核心。
 *
 * <p>负责根据 {@link AlertRule.AlertType}、告警正文与关联信息，
 * 组装一条 {@link AlertNotificationInfo} 通知值对象。</p>
 *
 * <p>FP-Java Profile 合规：</p>
 * <ul>
 *   <li>纯静态方法，无 Spring 注解、无 Repository/Service 依赖、无 IO</li>
 *   <li>不抛业务异常：relatedId 解析失败以 null 字段表达（无 try-catch）</li>
 *   <li>不返回 null：始终返回非 null 的 {@link AlertNotificationInfo}</li>
 *   <li>无 setter、无状态、无副作用</li>
 * </ul>
 */
public final class AlertMessagePolicy {

    /** CA_EXPIRY 已过期标识，alertMessage 含此串时映射为 CA_EXPIRED。 */
    private static final String CA_EXPIRED_KEYWORD = "已过期";

    /** 数值 ID 的合法字符集（1-18 位数字，避免 long 溢出）。 */
    private static final String NUMERIC_ID_PATTERN = "\\d{1,18}";

    /** relatedId 中实体类型与 ID 的分隔符。 */
    private static final String RELATED_ID_SEPARATOR = ":";

    private AlertMessagePolicy() {
        // 纯核心工具类，禁止实例化
    }

    /**
     * 根据告警类型与上下文构建通知信息。
     *
     * <p>始终返回非 null 对象。解析失败的字段以 null 表达。</p>
     *
     * @param type          告警类型（非 null，决定 notificationType 与 title）
     * @param alertMessage  告警正文（直接作为通知 body，可为 null）
     * @param relatedId     关联实体 ID（格式 "EntityType:EntityId"，可为 null）
     * @param extraPayload  附加载荷（取 targetUrl 键值，可为 null）
     * @return 非 null 的 {@link AlertNotificationInfo}
     */
    public static AlertNotificationInfo buildNotification(
            AlertRule.AlertType type,
            String alertMessage,
            String relatedId,
            Map<String, Object> extraPayload
    ) {
        String notificationType = resolveNotificationType(type, alertMessage);
        String title = resolveTitle(type);
        String targetUrl = resolveTargetUrl(extraPayload);
        String[] sourceEntity = parseRelatedId(relatedId);
        String sourceEntityType = sourceEntity[0];
        Long sourceEntityId = sourceEntity[1] == null ? null : Long.parseLong(sourceEntity[1]);

        return new AlertNotificationInfo(
                notificationType,
                title,
                alertMessage,
                sourceEntityType,
                sourceEntityId,
                targetUrl
        );
    }

    /**
     * 解析通知类型。CA_EXPIRY 根据 alertMessage 是否含"已过期"区分 CA_EXPIRED / CA_EXPIRING。
     */
    private static String resolveNotificationType(AlertRule.AlertType type, String alertMessage) {
        if (type == AlertRule.AlertType.CA_EXPIRY) {
            if (alertMessage != null && alertMessage.contains(CA_EXPIRED_KEYWORD)) {
                return "CA_EXPIRED";
            }
            return "CA_EXPIRING";
        }
        return switch (type) {
            case DEADLINE -> "DEADLINE";
            case RISK -> "SYSTEM";
            case DOCUMENT -> "DOCUMENT_CHANGE";
            case BUDGET -> "SYSTEM";
            case DEPOSIT_RETURN -> "DEADLINE";
            case PERFORMANCE_EXPIRY -> "DEADLINE";
            case QUALIFICATION_EXPIRY -> "DEADLINE";
            case CA_BORROW_OVERDUE -> "CA_BORROW_OVERDUE";
            // CA_EXPIRY 已在上方分支处理，此处不可达
            case CA_EXPIRY -> "CA_EXPIRING";
        };
    }

    /**
     * 解析中文标题。未知类型返回空串。
     */
    private static String resolveTitle(AlertRule.AlertType type) {
        return switch (type) {
            case DEADLINE -> "投标截止日期提醒";
            case RISK -> "风险评分提醒";
            case DOCUMENT -> "文档缺失提醒";
            case BUDGET -> "预算告警";
            case DEPOSIT_RETURN -> "保证金退还提醒";
            case PERFORMANCE_EXPIRY -> "业绩到期提醒";
            case CA_EXPIRY -> "CA证书到期提醒";
            case CA_BORROW_OVERDUE -> "CA借用超期提醒";
            case QUALIFICATION_EXPIRY -> "资质到期提醒";
        };
    }

    /**
     * 解析跳转链接。仅接受 String 类型，其他类型返回 null。
     */
    private static String resolveTargetUrl(Map<String, Object> extraPayload) {
        if (extraPayload == null) {
            return null;
        }
        Object value = extraPayload.get("targetUrl");
        if (value instanceof String s) {
            return s;
        }
        return null;
    }

    /**
     * 解析 relatedId（格式 "EntityType:EntityId"）。
     *
     * <p>FP-Java 合规：无 try-catch，先正则校验 ID 为纯数字（1-18 位，避免 long 溢出），
     * 再由调用方 {@link Long#parseLong(String)} 安全转换。</p>
     *
     * @return 长度为 2 的数组：[entityType, entityIdRaw]；解析失败时两个元素均为 null
     */
    private static String[] parseRelatedId(String relatedId) {
        if (relatedId == null || relatedId.isBlank()) {
            return new String[]{null, null};
        }
        int separatorIdx = relatedId.indexOf(RELATED_ID_SEPARATOR);
        if (separatorIdx <= 0) {
            // 分隔符不存在或位于首位 → 实体类型为空
            return new String[]{null, null};
        }
        String entityType = relatedId.substring(0, separatorIdx);
        String entityIdRaw = relatedId.substring(separatorIdx + 1);
        if (entityType.isBlank() || !entityIdRaw.matches(NUMERIC_ID_PATTERN)) {
            return new String[]{null, null};
        }
        // 仅允许单分隔符：原始串中再出现分隔符视为格式错误
        if (relatedId.indexOf(RELATED_ID_SEPARATOR, separatorIdx + 1) >= 0) {
            return new String[]{null, null};
        }
        return new String[]{entityType, entityIdRaw};
    }
}
