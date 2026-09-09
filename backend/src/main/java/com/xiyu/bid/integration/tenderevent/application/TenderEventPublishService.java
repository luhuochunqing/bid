package com.xiyu.bid.integration.tenderevent.application;

import com.xiyu.bid.config.TraceConstants;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPayload;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPolicy;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventStatus;
import com.xiyu.bid.integration.tenderevent.infrastructure.sdk.TenderEventSnowflakeUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 标讯事件发布编排服务。
 *
 * <p>在标讯创建后触发，职责：
 * <ol>
 *   <li>按策略判断该标讯是否需要推送（排除 CRM 回发的标讯，避免回发循环）；</li>
 *   <li>构造携带标讯详情展示字段的消息体 {@link TenderEventPayload}；</li>
 *   <li>通过独立线程池异步发送，不阻塞主创建链路；</li>
 *   <li>记录事件流水（成功 {@code SENT} / 失败 {@code FAILED}），用于问题定位。</li>
 * </ol>
 */
@Service
@Slf4j
public class TenderEventPublishService {

    private final TenderEventPublishPort publisher;
    private final TenderEventLogPort eventLog;
    private final TenderEventPayloadMapper payloadMapper;
    private final java.util.concurrent.Executor tenderEventExecutor;
    /** 功能开关：禁用时跳过推送（不产生流水、不发送）。 */
    private final boolean enabled;

    public TenderEventPublishService(
            TenderEventPublishPort publisher,
            TenderEventLogPort eventLog,
            TenderEventPayloadMapper payloadMapper,
            @Qualifier("tenderEventExecutor") java.util.concurrent.Executor tenderEventExecutor,
            @Value("${xiyu.integrations.tender-event.sdk.enabled:false}") boolean enabled) {
        this.publisher = publisher;
        this.eventLog = eventLog;
        this.payloadMapper = payloadMapper;
        this.tenderEventExecutor = tenderEventExecutor;
        this.enabled = enabled;
    }

    /**
     * 标讯创建后触发事件推送（同步入口，异步发送）。
     *
     * <p>策略不满足时直接跳过，不产生流水。
     *
     * @param tender 已持久化的标讯
     */
    public void publishOnCreate(Tender tender) {
        if (!enabled) {
            return;
        }
        if (tender == null || tender.getId() == null) {
            return;
        }
        if (!TenderEventPolicy.shouldPublish(tender.getSourceType())) {
            log.debug("标讯 {} 来源 {} 不在推送范围，跳过事件推送",
                    tender.getId(), tender.getSourceType());
            return;
        }

        TenderEventCode code = TenderEventCode.BID_TENDER_CHANGE;
        TenderEventPayload payload = new TenderEventPayload(
                tender.getId(),
                tender.getExternalId(),
                tender.getTitle(),
                tender.getPurchaserName(),
                tender.getRegion(),
                tender.getRegistrationDeadline(),
                tender.getBidOpeningTime(),
                tender.getCustomerType(),
                tender.getPriority(),
                tender.getProjectType(),
                tender.getContactName(),
                tender.getContactPhone(),
                tender.getContactTel(),
                tender.getContactMail(),
                tender.getContactNature(),
                tender.getContactName2(),
                tender.getContactPhone2(),
                tender.getContactTel2(),
                tender.getContactMail2(),
                tender.getContactNature2());
        // 完整雪花 long 经无损 base36 编码并加 t/s 类型前缀，最长 14 位，长期满足 ≤18 限制。
        String traceId = generateTraceId();
        String spanId = TenderEventSnowflakeUtil.nextSpanId();
        String parentId = "0";

        TenderEventPublishCommand command = new TenderEventPublishCommand(code, payload, traceId, spanId, parentId);
        tenderEventExecutor.execute(() -> sendAndRecord(command));
    }

    /**
     * 异步执行真实发送并记录流水。
     */
    private void sendAndRecord(TenderEventPublishCommand command) {
        String content = payloadMapper.toJson(command.payload());
        boolean sent;
        String errorMessage = null;
        try {
            sent = publisher.publish(command);
        } catch (RuntimeException ex) {
            sent = false;
            errorMessage = truncate(ex.getMessage(), 1000);
            log.error("标讯事件推送异常 eventCode={} tenderId={}", command.eventCode().name(),
                    command.payload().tenderId(), ex);
        }
        TenderEventStatus status = sent ? TenderEventStatus.SENT : TenderEventStatus.FAILED;
        eventLog.record(new TenderEventLogRecord(
                command.payload().tenderId(),
                command.eventCode().name(),
                command.eventCode().source(),
                command.eventCode().topic(),
                command.traceId(),
                command.spanId(),
                command.parentId(),
                content,
                status,
                errorMessage
        ));
        if (sent) {
            log.info("标讯事件推送成功 eventCode={} tenderId={} traceId={}",
                    command.eventCode().name(), command.payload().tenderId(), command.traceId());
        } else {
            log.warn("标讯事件推送失败 eventCode={} tenderId={} reason={}",
                    command.eventCode().name(), command.payload().tenderId(), errorMessage);
        }
    }

    private static String generateTraceId() {
        String traceId = MDC.get(TraceConstants.MDC_TRACE_KEY);
        if (traceId != null && !traceId.isBlank() && traceId.length() <= 18) {
            return traceId.trim();
        }
        return TenderEventSnowflakeUtil.nextTraceId();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
