package com.xiyu.bid.integration.tenderevent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 标讯事件消息体 data 序列化（label: 纯映射，无业务决策）。
 *
 * <p>把纯核心的 {@link TenderEventPayload}（record）序列化为事件总线要求的 JSON 字符串。
 * 只放关键标识：{@code tenderId} 必填，{@code externalId} 为空时省略，避免传脏数据。
 */
@Service
@RequiredArgsConstructor
public final class TenderEventPayloadMapper {

    private final ObjectMapper objectMapper;

    /**
     * 序列化消息体 data。
     *
     * @param payload 标讯事件消息体
     * @return JSON 字符串，如 {@code {"tenderId":123,"externalId":"ext-1"}}
     */
    public String toJson(TenderEventPayload payload) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("tenderId", payload.tenderId());
        if (payload.externalId() != null && !payload.externalId().isBlank()) {
            node.put("externalId", payload.externalId());
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化标讯事件消息体: tenderId=" + payload.tenderId(), e);
        }
    }
}