package com.xiyu.bid.integration.tenderevent.application;

import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPayload;

/**
 * 标讯事件发布命令（纯数据、不可变）。
 *
 * <p>承载一次事件推送所需的全部参数：事件编码、消息体 data、链路追踪三元组。
 * 由编排层构造，交给基础设施层实现发送。
 */
public record TenderEventPublishCommand(
        TenderEventCode eventCode,
        TenderEventPayload payload,
        String traceId,
        String spanId,
        String parentId
) {
}