package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.xiyu.bid.config.TraceConstants;
import com.xiyu.bid.integration.tenderevent.domain.OpportunityPredictionEventPayload;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventEnumLabels;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 商机预测提醒事件发布器（基础设施层 WithEffect）。
 *
 * <p>复用 {@link TenderEventSdkSender} 走 SDK sendEvent 标准通道推送事件总线，
 * {@code data} 携带商机预测表格全量展示字段（项目名称/项目状态/招标主体/项目负责人/
 * 客户类型/开标时间/服务周期/预测招标时间，枚举转中文），链路追踪头写入 HTTP Header，
 * 与标讯事件线上行为一致。
 *
 * <p>复用 {@code xiyu.integrations.tender-event.sdk.enabled} 开关：关闭时不发送、不抛异常。
 * 独立于标讯事件管线，避免改动 {@link TenderEventSdkProducer} 造成回归。
 */
@Slf4j
@Component
public class OpportunityPredictionEventPublisher {

    private final TenderEventSdkProperties properties;
    private final TenderEventSdkSender sender;

    public OpportunityPredictionEventPublisher(TenderEventSdkProperties properties,
                                               TenderEventSdkSender sender) {
        this.properties = properties;
        this.sender = sender;
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
        // 对齐标讯：事件库 trace_id/span_id 为 varchar(19)，必须用雪花 ID（UUID 去横线后 32 字符会超长）
        String spanId = TenderEventSnowflakeUtil.nextSpanId();
        try {
            boolean result = sender.send(TenderEventCode.OPPORTUNITY_PREDICTION,
                    buildData(payload), traceId, spanId, traceId);
            log.info("商机预测事件推送响应 projectId={} result={}", payload.projectId(), result);
            return result;
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

    /**
     * 链路 traceId：优先复用 MDC 中的上游 traceId（须 ≤19 位以满足事件库 varchar(19)），
     * 否则用 {@link TenderEventSnowflakeUtil} 生成雪花 ID。与标讯 generateTraceId 行为一致。
     */
    private static String currentTraceId() {
        String traceId = MDC.get(TraceConstants.MDC_TRACE_KEY);
        return (traceId != null && !traceId.isBlank() && traceId.length() <= 19)
                ? traceId
                : TenderEventSnowflakeUtil.nextTraceId();
    }
}
