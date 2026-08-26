package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishCommand;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishPort;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 标讯事件发送端口实现（基础设施层 WithEffect）。
 *
 * <p>通过西域事件 SDK 的 {@link SystemInteractiveService#sendEvent} 推送到事件总线
 * {@code POST {serverRegisterUrl}/eventbus/publishEvent}，其中 {@code data} 为
 * {@code eventContent} 字符串（只放标讯关键标识 {@code tenderId} / {@code externalId}），
 * 链路追踪 {@code traceId/spanId/parentId} 经 {@code EventTrackReq} 写入 HTTP Header。
 * 发送细节统一由 {@link TenderEventHttpSender} 承担，本类只负责标讯载荷与上下文日志。
 *
 * <p>2026-08-26 反编译核实（ClientSDK-release_0.0.2）：SDK 的 {@code sendEvent} 把
 * {@code data} 字段取自 {@code eventContent} 入参，trace 信息走 HTTP Header，
 * 此前"SDK 把 data 硬编码为 trace"的判断不成立，故回归 SDK 标准发送通道，
 * 消除与 SDK 的 wire 契约漂移风险。
 */
@Slf4j
public class TenderEventSdkProducer implements TenderEventPublishPort {

    private final TenderEventSdkProperties properties;
    private final TenderEventHttpSender sender;

    public TenderEventSdkProducer(TenderEventSdkProperties properties,
                                  SystemInteractiveService systemInteractiveService) {
        this.properties = properties;
        this.sender = new TenderEventHttpSender(systemInteractiveService);
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