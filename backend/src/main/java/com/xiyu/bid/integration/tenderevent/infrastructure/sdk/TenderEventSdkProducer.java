package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.xiyu.bid.integration.tenderevent.application.TenderEventPayloadMapper;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishCommand;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishPort;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 标讯事件发送端口实现（基础设施层 WithEffect）。
 *
 * <p>经客户 SDK 标准通道
 * {@code SystemInteractiveService#sendEvent(eventCode, eventSource, eventContent, eventTrackReq)}
 * 推送（2026-08-27 客户明确要求，serviceName 是 SDK 契约必带路由字段），发送细节
 * 统一由 {@link TenderEventSdkSender} 承担，本类只负责标讯载荷与上下文日志。
 */
@Slf4j
public class TenderEventSdkProducer implements TenderEventPublishPort {

    private final TenderEventSdkSender sender;

    public TenderEventSdkProducer(TenderEventSdkSender sender) {
        this.sender = sender;
    }

    @Override
    public boolean publish(TenderEventPublishCommand command) {
        try {
            boolean result = sender.send(command.eventCode(), buildData(command),
                    command.traceId(), command.spanId(), command.parentId());
            log.info("标讯事件推送响应 eventCode={} tenderId={} result={}",
                    command.eventCode().name(), command.payload().tenderId(), result);
            return result;
        } catch (RuntimeException ex) {
            log.error("标讯事件推送异常 eventCode={} tenderId={}", command.eventCode().name(),
                    command.payload().tenderId(), ex);
            return false;
        }
    }

    private Map<String, Object> buildData(TenderEventPublishCommand command) {
        // 复用 TenderEventPayloadMapper.toData（唯一数据源），保证实际发送字段与流水留痕完全一致
        return TenderEventPayloadMapper.toData(command.payload());
    }
}