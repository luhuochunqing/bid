package com.xiyu.bid.integration.tenderevent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventEnumLabels;
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
 * {@code customerType/priority/projectType} 经 {@link TenderEventEnumLabels} 转中文标签；
 * 时间字段格式化为 {@code yyyy-MM-dd HH:mm}。
 */
@Service
@RequiredArgsConstructor
public final class TenderEventPayloadMapper {

    /** 时间字段推送格式（与标讯详情页展示一致）。 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
        putIfPresent(data, "customerType", TenderEventEnumLabels.customerTypeLabel(payload.customerType()));
        putIfPresent(data, "priority", TenderEventEnumLabels.priorityLabel(payload.priority()));
        putIfPresent(data, "projectType", TenderEventEnumLabels.projectTypeLabel(payload.projectType()));
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
}
