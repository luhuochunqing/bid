package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.common.req.EventTrackReq;
import com.ehsy.eventlibrary.clientsdk.common.resp.SendEventRespDto;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 推送（2026-08-27 客户明确要求），请求协议由客户 SDK 负责。
 */
@Slf4j
public class TenderEventSdkProducer implements TenderEventPublishPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final SystemInteractiveService interactiveService;

    public TenderEventSdkProducer(SystemInteractiveService interactiveService) {
        this.interactiveService = interactiveService;
    }

    @Override
    public boolean publish(TenderEventPublishCommand command) {
        try {
            EventTrackReq track = new EventTrackReq();
            track.setTraceId(command.traceId());
            track.setSpanId(command.spanId());
            track.setParentId(command.parentId());
            SendEventRespDto response = interactiveService.sendEvent(
                    command.eventCode().topic(),
                    command.eventCode().source(),
                    serializeData(buildData(command)),
                    track);
            boolean accepted = response != null && !Boolean.FALSE.equals(response.getResult());
            log.info("标讯事件推送响应 eventCode={} tenderId={} result={}", command.eventCode().name(),
                    command.payload().tenderId(), response == null ? null : response.getResult());
            return accepted;
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

    private static String serializeData(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化标讯事件 data 载荷", e);
        }
    }
}
