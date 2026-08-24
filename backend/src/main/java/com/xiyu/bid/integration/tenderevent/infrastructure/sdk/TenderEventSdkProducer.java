package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishCommand;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishPort;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

/**
 * 标讯事件发送端口实现（基础设施层 WithEffect）。
 *
 * <p>直连西域事件总线 {@code POST {serverRegisterUrl}/eventbus/publishEvent}，消息体
 * {@code {serviceName, eventTopic, eventSource, data}}，其中 {@code data} 为可自定义的
 * {@link Map}（只放标讯关键标识 {@code tenderId} / {@code externalId}），链路追踪
 * {@code traceId/spanId/parentId} 写入 HTTP Header（与 SDK 线上行为一致）。
 * 发送细节统一由 {@link TenderEventHttpSender} 承担，本类只负责标讯载荷与上下文日志。
 *
 * <p>不使用 SDK 的 {@code sendEvent(..., EventTrackReq)}：该接口把 {@code data} 硬编码为
 * trace 信息，无法携带业务关键标识，故直连接口以完整支持自定义 {@code data}。
 */
@Slf4j
public class TenderEventSdkProducer implements TenderEventPublishPort {

    private final TenderEventSdkProperties properties;
    private final TenderEventHttpSender sender;

    public TenderEventSdkProducer(TenderEventSdkProperties properties, RestClient restClient) {
        this.properties = properties;
        this.sender = new TenderEventHttpSender(restClient, properties);
    }

    @Override
    public boolean publish(TenderEventPublishCommand command) {
        try {
            boolean result = sender.send(command.eventCode(), buildData(command),
                    command.traceId(), command.spanId(), command.parentId());
            log.info("标讯事件直连推送响应 eventCode={} tenderId={} result={}",
                    command.eventCode().name(), command.payload().tenderId(), result);
            return result;
        } catch (RuntimeException ex) {
            log.error("标讯事件直连推送异常 eventCode={} tenderId={}", command.eventCode().name(),
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
}