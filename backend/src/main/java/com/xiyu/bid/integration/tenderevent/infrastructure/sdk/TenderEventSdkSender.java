package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.alibaba.fastjson.JSONObject;
import com.ehsy.eventlibrary.clientsdk.common.enums.HttpInterfaceTypeEnum;
import com.ehsy.eventlibrary.clientsdk.common.req.EventTrackReq;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.impl.SystemInteractiveServiceImpl;
import com.ehsy.eventlibrary.clientsdk.util.HttpClientUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 西域事件总线发送器（基础设施层 WithEffect，标讯与商机预测事件共用）。
 *
 * <p>body 按客户规范与成功样例完全对齐：
 * <pre>
 * {
 *   "traceId": "...",
 *   "spanId": "...",
 *   "parentId": "0",
 *   "eventTopic": "BidTenderChange",
 *   "eventSource": "bid",
 *   "key": "tenderId",             // 业务主键字段名，非具体数值
 *   "time": 1787825789249,
 *   "data": "{\"tenderId\":2307}" // JSON 字符串
 * }
 * </pre>
 */
@Slf4j
final class TenderEventSdkSender {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String serverRegisterUrl;
    private final String serviceName;

    TenderEventSdkSender(String serverRegisterUrl, String serviceName) {
        this.serverRegisterUrl = serverRegisterUrl;
        this.serviceName = serviceName;
    }

    boolean send(TenderEventCode code, Map<String, Object> data,
                 String traceId, String spanId, String parentId) {
        EventTrackReq trackReq = new EventTrackReq();
        trackReq.setTraceId(traceId);
        trackReq.setSpanId(spanId);
        trackReq.setParentId(parentId);

        String content = serializeData(data);

        JSONObject body = new JSONObject();
        body.put("traceId", traceId);
        body.put("spanId", spanId);
        body.put("parentId", parentId != null ? parentId : "0");
        body.put("eventTopic", code.topic());
        body.put("eventSource", code.source());
        body.put("serviceName", serviceName);
        // 关键修复：key 必须是业务主键字段名（如 tenderId），而不是具体值
        body.put("key", code.dataIdField());
        body.put("time", System.currentTimeMillis());
        body.put("data", content);

        log.info("事件总线请求 eventTopic={} body={}", code.topic(), body.toJSONString());

        try {
            JSONObject resp = HttpClientUtil.httpPost(
                    serverRegisterUrl + "/eventbus/publishEvent",
                    body,
                    SystemInteractiveServiceImpl.buildHeaderParamMap(trackReq),
                    HttpInterfaceTypeEnum.HTTP_PUBLISH_EVENT.getValue()
            );
            log.info("事件总线响应 eventTopic={} response={}", code.topic(), resp != null ? resp.toJSONString() : "null");
            return interpretResult(resp);
        } catch (RuntimeException ex) {
            log.error("事件总线发送异常 eventTopic={}", code.topic(), ex);
            return false;
        }
    }

    private static boolean interpretResult(JSONObject resp) {
        if (resp == null) {
            return false;
        }
        if (resp.containsKey("code") && !"00000".equals(resp.getString("code"))) {
            return false;
        }
        Object data = resp.get("data");
        if (data instanceof JSONObject jsonObject) {
            return !Boolean.FALSE.equals(jsonObject.getBoolean("result"));
        }
        return true;
    }

    private static String serializeData(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化事件总线 data 载荷", e);
        }
    }
}
