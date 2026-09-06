package com.xiyu.bid.integration.tenderevent.domain;

import java.util.Map;

/**
 * 标讯/商机事件载荷的枚举码 → 中文标签映射（纯核心、无副作用）。
 *
 * <p>事件侧唯一标签源，供标讯 {@code TenderEventPayloadMapper.toData} 与商机
 * {@code OpportunityPredictionEventPublisher.buildData} 共用，避免两侧各自维护漂移。
 * 与 {@code ProjectExportService}、前端 {@code projectListFormatters.js} 同源；
 * 未知值 fallback 原值，避免历史/未知数据丢失（与前端 {@code map[v] || v} 行为一致）。
 */
public final class TenderEventEnumLabels {

    /** 项目状态 resultType → 中文。 */
    private static final Map<String, String> BID_STATUS_LABELS = Map.of(
            "PENDING_INITIATION", "待立项",
            "INITIATED", "已立项",
            "BIDDING", "投标中",
            "EVALUATING", "评标中",
            "WON", "已中标",
            "LOST", "未中标",
            "FAILED", "已流标",
            "ABANDONED", "已放弃");

    /** 客户类型 customerType → 中文。 */
    private static final Map<String, String> CUSTOMER_TYPE_LABELS = Map.of(
            "GOVERNMENT", "政府机关/事业单位/高校",
            "CENTRAL_SOE", "央企",
            "LOCAL_SOE", "地方国企",
            "PRIVATE", "民企",
            "FOREIGN", "港澳台及外企",
            "OTHER", "其他");

    /** 项目类型 projectType → 中文。 */
    private static final Map<String, String> PROJECT_TYPE_LABELS = Map.of(
            "OFFICE", "办公",
            "COMPREHENSIVE", "综合",
            "COLLECTIVE", "集采",
            "INDUSTRIAL", "工业品",
            "OTHER", "其他");

    /** 优先级 priority → 中文（后端已归一化为 S/A/B/C）。 */
    private static final Map<String, String> PRIORITY_LABELS = Map.of(
            "S", "S级",
            "A", "A级",
            "B", "B级",
            "C", "C级");

    private TenderEventEnumLabels() {
    }

    /** 项目状态标签；null/空白返回 null（由调用方省略），未知值 fallback 原值。 */
    public static String bidStatusLabel(String value) {
        return mapLabel(BID_STATUS_LABELS, value);
    }

    /** 客户类型标签；null/空白返回 null（由调用方省略），未知值 fallback 原值。 */
    public static String customerTypeLabel(String value) {
        return mapLabel(CUSTOMER_TYPE_LABELS, value);
    }

    /** 项目类型标签；null/空白返回 null（由调用方省略），未知值 fallback 原值。 */
    public static String projectTypeLabel(String value) {
        return mapLabel(PROJECT_TYPE_LABELS, value);
    }

    /** 优先级标签；null/空白返回 null（由调用方省略），未知值 fallback 原值。 */
    public static String priorityLabel(String value) {
        return mapLabel(PRIORITY_LABELS, value);
    }

    private static String mapLabel(Map<String, String> labels, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return labels.getOrDefault(value, value);
    }
}
