package com.xiyu.bid.integration.tenderevent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPayload;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 标讯事件消息体 data 序列化（label: 纯映射，无业务决策）。
 *
 * <p>把纯核心的 {@link TenderEventPayload}（record）投影为事件总线要求的 data 结构：
 * {@link #toData} 产出有序 Map（唯一数据源，供流水留痕与实际发送共用，避免两处字段漂移），
 * {@link #toJson} 再序列化为 JSON 字符串写入 {@code tender_event_logs.event_content}。
 *
 * <p>字段规则：{@code tenderId} 必填；其余字段为 null/空白时省略，避免传脏数据；
 * {@code customerType/priority/projectType} 转中文标签（与 {@code ProjectExportService}、
 * 前端 {@code projectListFormatters.js} 同源，未知值 fallback 原值）；时间字段格式化为
 * {@code yyyy-MM-dd HH:mm}。
 */
@Service
@RequiredArgsConstructor
public final class TenderEventPayloadMapper {

    /** 时间字段推送格式（与标讯详情页展示一致）。 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── 枚举中文映射（与 ProjectExportService / 前端 projectListFormatters.js 同源）──
    // 未知值 fallback 原值，避免历史/未知数据丢失（与前端 map[v] || v 行为一致）。

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

    private final ObjectMapper objectMapper;

    /**
     * 序列化消息体 data（写入 {@code tender_event_logs.event_content} 流水留痕）。
     *
     * @param payload 标讯事件消息体
     * @return JSON 字符串
     */
    public String toJson(TenderEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(toData(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化标讯事件消息体: tenderId=" + payload.tenderId(), e);
        }
    }

    /**
     * 把标讯载荷投影为事件总线 data（有序 Map，唯一数据源）。
     *
     * <p>{@link #toJson}（流水留痕）与 {@code TenderEventSdkProducer}（实际发送）共用本方法，
     * 保证两者字段集与中文映射完全一致，不会因分别维护而漂移。
     *
     * @param payload 标讯事件消息体
     * @return 有序 data Map；null/空白字段已省略，枚举字段已转中文，时间字段已格式化
     */
    public static Map<String, Object> toData(TenderEventPayload payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenderId", payload.tenderId());
        putIfPresent(data, "externalId", payload.externalId());
        putIfPresent(data, "title", payload.title());
        putIfPresent(data, "purchaserName", payload.purchaserName());
        putIfPresent(data, "region", payload.region());
        putDateTimeIfPresent(data, "registrationDeadline", payload.registrationDeadline());
        putDateTimeIfPresent(data, "bidOpeningTime", payload.bidOpeningTime());
        putIfPresent(data, "customerType", mapLabel(CUSTOMER_TYPE_LABELS, payload.customerType()));
        putIfPresent(data, "priority", mapLabel(PRIORITY_LABELS, payload.priority()));
        putIfPresent(data, "projectType", mapLabel(PROJECT_TYPE_LABELS, payload.projectType()));
        putIfPresent(data, "contactName", payload.contactName());
        putIfPresent(data, "contactPhone", payload.contactPhone());
        putIfPresent(data, "contactTel", payload.contactTel());
        putIfPresent(data, "contactMail", payload.contactMail());
        putIfPresent(data, "contactNature", payload.contactNature());
        putIfPresent(data, "contactName2", payload.contactName2());
        putIfPresent(data, "contactPhone2", payload.contactPhone2());
        putIfPresent(data, "contactTel2", payload.contactTel2());
        putIfPresent(data, "contactMail2", payload.contactMail2());
        putIfPresent(data, "contactNature2", payload.contactNature2());
        return data;
    }

    /** 字符串字段：null/空白省略。 */
    private static void putIfPresent(Map<String, Object> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value);
        }
    }

    /** 时间字段：null 省略，否则格式化为 {@code yyyy-MM-dd HH:mm}。 */
    private static void putDateTimeIfPresent(Map<String, Object> data, String key, LocalDateTime value) {
        if (value != null) {
            data.put(key, value.format(DATE_TIME_FORMATTER));
        }
    }

    /** 枚举值 → 中文；null/空白返回 null（由调用方省略），未知值 fallback 原值。 */
    private static String mapLabel(Map<String, String> labels, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return labels.getOrDefault(value, value);
    }
}
