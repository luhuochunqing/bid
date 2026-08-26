package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.common.req.EventTrackReq;
import com.ehsy.eventlibrary.clientsdk.common.resp.SendEventRespDto;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 西域事件总线发送器（基础设施层 WithEffect，包内共享）。
 *
 * <p>统一委托 SDK 的 {@link SystemInteractiveService#sendEvent} 上报
 * {@code POST {serverRegisterUrl}/eventbus/publishEvent}：消息体
 * {@code {serviceName, eventTopic, eventSource, data}} 组装、traceId/spanId/parentId
 * 写入 HTTP Header、HTTP 调用均由 SDK 承担，本类只负责业务载荷序列化与结果解释。
 *
 * <p>结果解释与 SDK 原生语义的差异：真实网关成功时返回外层 {@code code=="00000"}
 * 且 {@code data.result} 为 null（2026-08-25 部署实测），SDK 只透传 {@code data.result}，
 * 故本类将 result=null 视为已受理成功，仅显式 result=false 判为失败。
 *
 * <p>已知 SDK 行为：其 HTTP 层仅在状态码 200 时解析响应体，网关返回 201 等非 200
 * 状态码时 SDK 内部抛 NPE，由调用方按异常降级记录 FAILED。
 *
 * <p>标讯事件与商机预测提醒事件共用同一总线，两者都通过本类发送。
 * 网络异常向上抛，由调用方按上下文记录日志并降级。
 */
@Slf4j
final class TenderEventHttpSender {

    /** 事件总线 {@code EventContentReqDto.data} 声明为 String，须将业务载荷序列化为 JSON 字符串上报。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SystemInteractiveService sdkService;

    TenderEventHttpSender(SystemInteractiveService sdkService) {
        this.sdkService = sdkService;
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

        SendEventRespDto resp = sdkService.sendEvent(
                code.topic(), code.source(), serializeData(data), trackReq);
        return resp != null && !Boolean.FALSE.equals(resp.getResult());
    }

    private static String serializeData(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化事件总线 data 载荷", e);
        }
    }
}
