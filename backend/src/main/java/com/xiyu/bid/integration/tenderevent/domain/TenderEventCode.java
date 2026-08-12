package com.xiyu.bid.integration.tenderevent.domain;

/**
 * 标讯事件编码（纯核心、不可变）。
 *
 * <p>{@code topic} / {@code source} 为占位值（待与西域 CRM 联调确认）：
 * 事件推送走西域事件 SDK 的 {@code /eventbus/publishEvent}，消息体为
 * {@code {serviceName, eventTopic, eventSource, data}}。
 */
public enum TenderEventCode {
    BID_TENDER_CHANGE("BidTenderChange", "bid", "TENDER", "tenderId");

    private final String topic;
    private final String source;
    private final String entityType;
    private final String dataIdField;

    TenderEventCode(String topic, String source, String entityType, String dataIdField) {
        this.topic = topic;
        this.source = source;
        this.entityType = entityType;
        this.dataIdField = dataIdField;
    }

    public String topic() {
        return topic;
    }

    public String source() {
        return source;
    }

    public String entityType() {
        return entityType;
    }

    public String dataIdField() {
        return dataIdField;
    }
}