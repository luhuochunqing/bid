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
 * 西域事件总线 SDK 标准通道发送器（基础设施层 WithEffect，标讯与商机预测事件共用）。
 *
 * <p>直接调用客户 SDK 的
 * {@code SystemInteractiveService.sendEvent(eventCode, eventSource, eventContent, eventTrackReq)}，
 * 请求由 SDK 组装：body 固定 4 字段 {@code {serviceName, eventTopic, eventSource, data}}，
 * trace 三元组经 {@code EventTrackReq} 写入 HTTP Header。2026-08-27 客户反编译截图与
 * 本仓 jar（ClientSDK-release_0.0.2）字节码双重核实：
 * <b>serviceName 是 SDK 契约的必带字段</b>（SDK 从自身 {@code ServiceRegisterProperties} 取值
 * 放进 body），key/time/trace 不进 body——消息结构文档中的 9 字段是事件库内部存储形态，
 * 不是 SDK 的 wire 契约。此前手工拼 body 时省略 serviceName，网关受理成功（code=00000）
 * 但事件不进入投递链路（tenderId=2289~2296 客户事件库均未收到），客户亦明确要求改走
 * SDK sendEvent 标准通道。
 *
 * <p>serviceName 供给链：{@link TenderEventSdkConfig} 手工构造
 * {@code ServiceRegisterProperties}（serviceName 取标讯自己的
 * {@code TenderEventSdkProperties.serviceName}，测试环境=bid）反射注入 SDK 服务——
 * 不能注册为 Spring bean：该类自带类级 {@code @ConfigurationProperties(prefix="client.register")}，
 * 容器管理的实例会被组织消费侧环境值（BidSystemOrgConsumer）重新绑定覆盖，
 * 2026-08-27 实测被覆盖后网关返回 {@code result:false}（bid→受理成功对照证实）。
 *
 * <p>结果解释：网关成功返回 {@code {"code":"00000","data":{"result":null},"msg":"响应成功"}}，
 * SDK 内部 fastjson 解析丢弃 null 值后 {@code SendEventRespDto.result=null}——
 * result 为 null 或 true 均视为受理成功，仅显式 {@code result=false} 判失败。
 *
 * <p>已知 SDK 行为（字节码核实）：{@code HttpClientUtil.httpPost} 仅在状态码 200 时
 * 返回响应体，非 200 与 IO 异常静默返回 null；{@code sendEvent} 随后对 null 取
 * {@code "data"} 会抛 NullPointerException——本类捕获后判失败，不向调用方外泄异常。
 */
@Slf4j
final class TenderEventSdkSender {

    /** 网关 {@code EventContentReqDto.data} 声明为 String，业务载荷须序列化为 JSON 字符串。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SystemInteractiveService service;

    /** 仅用于对账日志；SDK 实际发送值由装配方喂给 SDK 的同名配置保证一致。 */
    private final String serviceName;

    TenderEventSdkSender(SystemInteractiveService service, String serviceName) {
        this.service = service;
        this.serviceName = serviceName;
    }

    /**
     * 发送事件并解释总线返回结果。
     *
     * @param code     事件类型（topic/source 来自 {@link TenderEventCode}）
     * @param data     业务关键标识载荷（将序列化为 JSON 字符串，作为 eventContent 传给 SDK）
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

        String content = serializeData(data);
        // 对账日志：serviceName/eventTopic/eventSource/data 即 SDK sendEvent 实际发出的 body 4 字段
        log.info("事件总线请求 eventTopic={} serviceName={} data={}", code.topic(), serviceName, content);

        try {
            SendEventRespDto resp = service.sendEvent(code.topic(), code.source(), content, trackReq);
            boolean ok = !Boolean.FALSE.equals(resp.getResult());
            log.info("事件总线响应 eventTopic={} result={} ok={}", code.topic(), resp.getResult(), ok);
            return ok;
        } catch (RuntimeException ex) {
            log.error("事件总线发送异常 eventTopic={}", code.topic(), ex);
            return false;
        }
    }

    private static String serializeData(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化事件总线 data 载荷", e);
        }
    }
}
