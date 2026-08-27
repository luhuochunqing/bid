package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.alibaba.fastjson.JSONObject;
import com.ehsy.eventlibrary.clientsdk.common.enums.HttpInterfaceTypeEnum;
import com.ehsy.eventlibrary.clientsdk.common.req.EventTrackReq;
import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRegisterProperties;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.impl.SystemInteractiveServiceImpl;
import com.ehsy.eventlibrary.clientsdk.util.HttpClientUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 西域事件总线发送器（基础设施层 WithEffect，包内共享）。
 *
 * <p>消息体按 SDK {@code sendEvent} 原始契约组装：
 * <pre>{serviceName, eventTopic, eventSource, data}</pre>
 * traceId/spanId/parentId 写入 HTTP Header（SDK {@code buildHeaderParamMap} 既有行为）。
 * 客户文档的"消息结构"（含 eventCode/time/key 等字段）是网关**内部消息格式**，
 * 由网关自行填充，不放在 HTTP 请求 body 中。此前误将内部字段塞入 body 导致
 * 网关返回 data:{} 但事件未实际发布（tenderId=2289 实测）。
 *
 * <p>parentId 须遵循客户文档：无父链路时 parentId="0"
 * （{@code EventHandleService.sendEvent} 参考实现），不是 traceId 的值。
 *
 * <p>结果解释与 SDK 原生语义的差异：真实网关成功时返回外层 {@code code=="00000"}
 * 且 {@code data.result} 为 null（2026-08-25 部署实测），故 result=null 视为已受理成功，
 * 仅显式 result=false 判为失败；响应缺失 data 字段或 data 非对象时按失败处理
 * （SDK 原生路径会抛 NPE/JSONException，经调用方降级后同样是 FAILED，净效果一致）。
 *
 * <p>已知 SDK HTTP 行为（字节码核实）：仅状态码 200 时解析响应体并返回；非 200
 * 状态码与 IO 异常均被 {@code HttpClientUtil} 静默吞掉后返回 null（SDK 原生路径
 * 随后在 {@code sendEvent} 内以 NPE 暴露），本类将 null 直接判为失败，净效果一致。
 *
 * <p>标讯事件与商机预测提醒事件共用同一总线，两者都通过本类发送。
 */
@Slf4j
final class TenderEventHttpSender {

    /** 事件总线 {@code EventContentReqDto.data} 声明为 String，须将业务载荷序列化为 JSON 字符串上报。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ServiceRegisterProperties registerProperties;

    TenderEventHttpSender(ServiceRegisterProperties registerProperties) {
        this.registerProperties = registerProperties;
    }

    /**
     * 发送事件并解释总线返回结果。
     *
     * @param code     事件类型（topic/source 来自 {@link TenderEventCode}）
     * @param data     业务关键标识载荷
     * @param traceId  链路追踪 traceId
     * @param spanId   链路追踪 spanId
     * @param parentId 链路追踪 parentId
     * @return 总线受理成功（result 非 false）时为 true
     */
    boolean send(TenderEventCode code, Map<String, Object> data,
                 String traceId, String spanId, String parentId) {
        EventTrackReq trackReq = new EventTrackReq();
        trackReq.setTraceId(traceId);
        trackReq.setSpanId(spanId);
        trackReq.setParentId(parentId);

        JSONObject body = new JSONObject();
        body.put("serviceName", registerProperties.getServiceName());
        body.put("eventTopic", code.topic());
        body.put("eventSource", code.source());
        body.put("data", serializeData(data));

        // 请求体落日志：联调对账用
        log.info("事件总线请求 eventTopic={} body={}", code.topic(), body);

        JSONObject resp = HttpClientUtil.httpPost(
                registerProperties.getServerRegisterUrl() + "/eventbus/publishEvent",
                body,
                SystemInteractiveServiceImpl.buildHeaderParamMap(trackReq),
                HttpInterfaceTypeEnum.HTTP_PUBLISH_EVENT.getValue());

        // 原始响应体落日志：保留网关第一手响应原文，供联调对账与后续契约核对
        log.info("事件总线原始响应 eventTopic={} response={}", code.topic(), resp);

        return interpretResult(resp);
    }

    private static boolean interpretResult(JSONObject resp) {
        return resp != null && resp.get("data") instanceof JSONObject content
                && !Boolean.FALSE.equals(content.get("result"));
    }

    private static String serializeData(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化事件总线 data 载荷", e);
        }
    }
}
