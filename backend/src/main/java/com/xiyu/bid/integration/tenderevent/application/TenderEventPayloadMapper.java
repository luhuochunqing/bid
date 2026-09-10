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
 * {@link #toData} 产出有序 Map（唯一数据源，供流水留痕与实际发送共用，
 * 避免两处字段漂移），
 * {@link #toJson} 再序列化为 JSON 字符串写入 {@code tender_event_logs.event_content}。
 *
 * <p>字段规则：{@code tenderId} 必填；其余约定字段始终输出，null/空白统一写为空字符串；
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
     * @return 有序 data Map；null/空白字段为空字符串，枚举字段已转中文，时间字段已格式化
     */
    public static Map<String, Object> toData(TenderEventPayload payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenderId", payload.tenderId());
        data.put("externalId", emptyIfBlank(payload.externalId()));
        data.put("title", emptyIfBlank(payload.title()));
        data.put("purchaserName", emptyIfBlank(payload.purchaserName()));
        data.put("region", emptyIfBlank(payload.region()));
        data.put("registrationDeadline", formatOrEmpty(payload.registrationDeadline()));
        data.put("bidOpeningTime", formatOrEmpty(payload.bidOpeningTime()));
        data.put("customerType", emptyIfBlank(TenderEventEnumLabels.customerTypeLabel(payload.customerType())));
        data.put("priority", emptyIfBlank(TenderEventEnumLabels.priorityLabel(payload.priority())));
        data.put("projectType", emptyIfBlank(TenderEventEnumLabels.projectTypeLabel(payload.projectType())));
        data.put("contactName", emptyIfBlank(payload.contactName()));
        data.put("contactPhone", emptyIfBlank(payload.contactPhone()));
        data.put("contactTel", emptyIfBlank(payload.contactTel()));
        data.put("contactMail", emptyIfBlank(payload.contactMail()));
        data.put("contactNature", emptyIfBlank(payload.contactNature()));
        data.put("contactName2", emptyIfBlank(payload.contactName2()));
        data.put("contactPhone2", emptyIfBlank(payload.contactPhone2()));
        data.put("contactTel2", emptyIfBlank(payload.contactTel2()));
        data.put("contactMail2", emptyIfBlank(payload.contactMail2()));
        data.put("contactNature2", emptyIfBlank(payload.contactNature2()));
        return data;
    }

    /** 客户契约要求字段不缺省：null/空白统一为空字符串。 */
    private static String emptyIfBlank(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    /** 时间字段：null 为空字符串，否则格式化为 {@code yyyy-MM-dd HH:mm}。 */
    private static String formatOrEmpty(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }
}
