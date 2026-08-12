package com.xiyu.bid.integration.tenderevent.application;

import com.xiyu.bid.integration.tenderevent.domain.TenderEventStatus;

/**
 * 标讯事件推送流水记录（纯数据、不可变）。
 *
 * <p>用于问题定位：记录每次推送的目标标讯、事件编码、链路追踪信息、发送结果与失败原因。
 */
public record TenderEventLogRecord(
        Long tenderId,
        String eventCode,
        String eventSource,
        String eventTopic,
        String traceId,
        String spanId,
        String parentId,
        String eventContent,
        TenderEventStatus status,
        String errorMessage
) {
}