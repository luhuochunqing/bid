package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.common.req.EventTrackReq;
import com.ehsy.eventlibrary.clientsdk.common.resp.SendEventRespDto;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.config.TraceConstants;
import com.xiyu.bid.integration.tenderevent.domain.OpportunityPredictionEventPayload;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventEnumLabels;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * 商机预测提醒事件发布器（基础设施层 WithEffect）。
 *
 * <p>直接走 SDK sendEvent 标准通道推送事件总线，
 * {@code data} 携带商机预测表格全量展示字段（项目名称/项目状态/招标主体/项目负责人/
 * 客户类型/开标时间/服务周期/预测招标时间，枚举转中文），链路追踪头写入 HTTP Header，
 * 与标讯事件线上行为一致。
 *
 * <p>复用 {@code xiyu.integrations.tender-event.sdk.enabled} 开关：关闭时不发送、不抛异常。
 * 独立于标讯事件管线，避免改动 {@link TenderEventSdkProducer} 造成回归。
 */
@Slf4j
public class OpportunityPredictionEventPublisher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final TenderEventSdkProperties properties;
    private final SystemInteractiveService interactiveService;

    public OpportunityPredictionEventPublisher(TenderEventSdkProperties properties,
                                               SystemInteractiveService interactiveService) {
        this.properties = properties;
        this.interactiveService = interactiveService;
    }

    /**
     * 发布商机预测提醒事件。
     *
     * @param payload 商机预测事件载荷
     * @return 是否发送成功；开关关闭、入参缺失、网络异常时返回 false（不抛异常）
     */
    public boolean publish(final OpportunityPredictionEventPayload payload) {
        if (!properties.enabled() || payload == null || payload.projectId() == null) {
            return false;
        }
        String traceId = currentTraceId();
        String spanId = TenderEventSnowflakeUtil.nextSpanId();
        try {
            EventTrackReq track = new EventTrackReq();
            track.setTraceId(traceId);
            track.setSpanId(spanId);
            track.setParentId("0");
            TenderEventCode code = TenderEventCode.OPPORTUNITY_PREDICTION;
            SendEventRespDto response = interactiveService.sendEvent(
                    code.topic(), code.source(), serializeData(buildData(payload)), track);
            boolean accepted = response != null && !Boolean.FALSE.equals(response.getResult());
            log.info("商机预测事件推送响应 projectId={} result={}", payload.projectId(),
                    response == null ? null : response.getResult());
            return accepted;
        } catch (RuntimeException ex) {
            log.error("商机预测事件推送异常 projectId={}", payload.projectId(), ex);
            return false;
        }
    }

    private Map<String, Object> buildData(final OpportunityPredictionEventPayload payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", payload.projectId());
        putIfPresent(data, "projectName", payload.projectName());
        putIfPresent(data, "resultType", TenderEventEnumLabels.bidStatusLabel(payload.resultType()));
        putIfPresent(data, "customer", payload.customer());
        putIfPresent(data, "projectLeaderName", payload.projectLeaderName());
        putIfPresent(data, "customerType", TenderEventEnumLabels.customerTypeLabel(payload.customerType()));
        putIfPresent(data, "bidOpenTime", payload.bidOpenTime());
        if (payload.servicePeriodYears() != null) {
            data.put("servicePeriodYears", payload.servicePeriodYears());
        }
        if (payload.servicePeriodEndDate() != null) {
            data.put("servicePeriodEndDate", payload.servicePeriodEndDate().toString());
        }
        if (payload.predictedBidDate() != null) {
            data.put("predictedBidDate", payload.predictedBidDate().toString());
        }
        return data;
    }

    /** 字符串字段：null/空白省略。 */
    private static void putIfPresent(final Map<String, Object> data, final String key, final String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value);
        }
    }

    private static String serializeData(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化商机预测事件 data 载荷", e);
        }
    }

    /**
     * 链路 traceId：优先复用 MDC 中不超过 18 位的非空上游 traceId，
     * 否则用 {@link TenderEventSnowflakeUtil} 生成雪花 ID。与标讯 generateTraceId 行为一致。
     */
    private static String currentTraceId() {
        String traceId = MDC.get(TraceConstants.MDC_TRACE_KEY);
        return (traceId != null && !traceId.isBlank() && traceId.length() <= 18)
                ? traceId.trim()
                : TenderEventSnowflakeUtil.nextTraceId();
    }
}
