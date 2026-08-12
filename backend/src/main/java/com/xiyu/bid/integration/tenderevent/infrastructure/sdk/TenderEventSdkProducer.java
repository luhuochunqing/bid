package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishCommand;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishPort;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * 标讯事件发送端口实现（基础设施层 WithEffect）。
 *
 * <p>直连西域事件总线 {@code POST {serverRegisterUrl}/eventbus/publishEvent}，消息体
 * {@code {serviceName, eventTopic, eventSource, data}}，其中 {@code data} 为可自定义的
 * {@link Map}（只放标讯关键标识 {@code tenderId} / {@code externalId}），链路追踪
 * {@code traceId/spanId/parentId} 写入 HTTP Header（与 SDK 线上行为一致）。
 *
 * <p>不使用 SDK 的 {@code sendEvent(..., EventTrackReq)}：该接口把 {@code data} 硬编码为
 * trace 信息，无法携带业务关键标识，故直连接口以完整支持自定义 {@code data}。
 */
@Slf4j
@RequiredArgsConstructor
public class TenderEventSdkProducer implements TenderEventPublishPort {

    private final TenderEventSdkProperties properties;
    private final RestClient restClient;

    @Override
    public boolean publish(TenderEventPublishCommand command) {
        TenderEventCode code = command.eventCode();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceName", properties.serviceName());
        body.put("eventTopic", code.topic());
        body.put("eventSource", code.source());
        body.put("data", buildData(command));

        try {
            JsonNode response = restClient.post()
                    .uri("/eventbus/publishEvent")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyTraceHeaders(headers, command))
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            boolean result = response != null && response.path("data").path("result").asBoolean(false);
            log.info("标讯事件直连推送响应 eventCode={} tenderId={} result={}",
                    code.name(), command.payload().tenderId(), result);
            return result;
        } catch (RuntimeException ex) {
            log.error("标讯事件直连推送异常 eventCode={} tenderId={}", code.name(),
                    command.payload().tenderId(), ex);
            return false;
        }
    }

    private Map<String, Object> buildData(TenderEventPublishCommand command) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenderId", command.payload().tenderId());
        if (command.payload().externalId() != null && !command.payload().externalId().isBlank()) {
            data.put("externalId", command.payload().externalId());
        }
        return data;
    }

    private void applyTraceHeaders(HttpHeaders headers, TenderEventPublishCommand command) {
        headers.add("traceId", command.traceId());
        headers.add("spanId", command.spanId());
        headers.add("parentId", command.parentId());
    }
}