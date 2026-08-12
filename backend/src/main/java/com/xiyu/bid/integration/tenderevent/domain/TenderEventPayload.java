package com.xiyu.bid.integration.tenderevent.domain;

/**
 * 标讯事件消息体 data（纯核心、不可变）。
 *
 * <p>只放关键标识，用于 CRM 定位标讯；不携带完整标讯字段，避免新老数据/字段差异问题。
 */
public record TenderEventPayload(Long tenderId, String externalId) {
}