package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.common.req.EventTrackReq;
import com.ehsy.eventlibrary.clientsdk.common.resp.SendEventRespDto;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 西域事件总线 SDK 标准通道发送器（基础设施层 WithEffect，标讯与商机预测事件共用）。
 *
 * <p>直接调用客户 SDK 的
 * {@code SystemInteractiveService.sendEvent(eventCode, eventSource, eventContent, eventTrackReq)}，
 * 请求由 SDK 组装：body 固定 4 字段 {@code {serviceName, eventTopic, eventSource, data}}，
 * trace 三元组经 {@code EventTrackReq} 写入 HTTP Header。
 */
@Slf4j
final class TenderEventSdkSender {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SystemInteractiveService service;
    private final String serviceName;

    TenderEventSdkSender(SystemInteractiveService service, String serviceName) {
        this.service = service;
        this.serviceName = serviceName;
    }

    boolean send(TenderEventCode code, Map<String, Object> data,
                 String traceId, String spanId, String parentId) {
        EventTrackReq trackReq = new EventTrackReq();
        trackReq.setTraceId(traceId);
        trackReq.setSpanId(spanId);
        trackReq.setParentId(parentId);

        String content = serializeData(data);
        log.info("事件总线请求 eventTopic={} serviceName={} data={}", code.topic(), serviceName, content);

        try {
            SendEventRespDto resp = service.sendEvent(code.topic(), code.source(), content, trackReq);
            boolean ok = resp != null && !Boolean.FALSE.equals(resp.getResult());
            log.info("事件总线响应 eventTopic={} result={} ok={}", code.topic(), resp != null ? resp.getResult() : null, ok);
            return ok;
        } catch (RuntimeException ex) {
            log.error("事件总线发送异常 eventTopic={}", code.topic(), ex);
            return false;
        }
    }

    private static String serializeData(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化事件总线 data 载荷", e);
        }
    }
}
