package com.xiyu.bid.alerts.domain;

import com.xiyu.bid.alerts.entity.AlertRule;

import java.util.Map;
import java.util.Optional;

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

    /** payload 中存放告警子类型的 key（用于 CA_EXPIRY 区分 EXPIRED/EXPIRING 等）。 */
    public static final String PAYLOAD_KEY_ALERT_SUB_TYPE = "alertSubType";

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
     * @param extraPayload  附加载荷（取 targetUrl 和 alertSubType 键值，可为 null）
     * @return 非 null 的 {@link AlertNotificationInfo}
     */
    public static AlertNotificationInfo buildNotification(
            AlertRule.AlertType type,
            String alertMessage,
            String relatedId,
            Map<String, Object> extraPayload
    ) {
        String notificationType = resolveNotificationType(type, extraPayload);
        String title = resolveTitle(type);
        // P1-2: 使用共享的 RelatedIdParser 统一解析 relatedId
        Optional<RelatedIdParser.ParsedRelatedId> parsed = RelatedIdParser.parse(relatedId);
        String entityType = parsed.map(RelatedIdParser.ParsedRelatedId::entityType).orElse(null);
        Long entityId = parsed.map(RelatedIdParser.ParsedRelatedId::entityId).orElse(null);

        return new AlertNotificationInfo(
                notificationType,
                title,
                alertMessage,
                entityType,
                entityId,
                resolveTargetUrl(extraPayload)
        );
    }

    /**
     * 解析通知类型。每种 AlertType 映射到独立的 notificationType，前端可按类型筛选。
     *
     * <p>CA_EXPIRY 根据 payload 中 {@code alertSubType} 区分 CA_EXPIRED / CA_EXPIRING，
     * 不再依赖消息文案的字符串匹配（修复 P1-10 脆弱性）。</p>
     */
    private static String resolveNotificationType(AlertRule.AlertType type, Map<String, Object> extraPayload) {
        if (type == AlertRule.AlertType.CA_EXPIRY) {
            String subType = resolveAlertSubType(extraPayload);
            if ("EXPIRED".equals(subType)) {
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
            case CA_EXPIRY -> throw new IllegalStateException("CA_EXPIRY must be handled by resolveNotificationType branch above");
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
     * 从 payload 中提取告警子类型（如 CA_EXPIRY 的 EXPIRED/EXPIRING）。
     */
    private static String resolveAlertSubType(Map<String, Object> extraPayload) {
        if (extraPayload == null) {
            return null;
        }
        Object value = extraPayload.get(PAYLOAD_KEY_ALERT_SUB_TYPE);
        return value instanceof String s ? s : null;
    }
}
