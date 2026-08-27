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
 * 西域事件总线发送器（基础设施层 WithEffect，包内共享）。
 *
 * <p>body 按客户手动触发成功样例（2026-08-27，tenderId=2282）**完全复刻**：
 * <pre>{traceId, spanId, parentId, eventSource, eventTopic, key, time, data}</pre>
 * 客户成功样例（执行成功）：
 * <pre>
 * {
 *   "traceId": "748104276040151040",
 *   "spanId": "s748104276040151041",
 *   "data": "{\"tenderId\": \"2282\"}",  // JSON 字符串！EventContentReqDto.data 是 String
 *   "eventSource": "bid",
 *   "eventTopic": "BidTenderChange",
 *   "time": 1787792358513,             // 毫秒时间戳，客户文档标注为必需字段
 *   "key": "tenderId",                 // 业务主键字段名（部门事件=deptId、员工事件=userId）
 *   "parentId": "0"
 * }
 * </pre>
 * 网关 {@code EventContentReqDto.data} 声明为 String（400 实测证实：传对象报
 * "Can not deserialize instance of java.lang.String out of START_OBJECT token"），
 * 必须序列化为 JSON 字符串上报。成功信号实测为 {@code HTTP 200 +
 * {"code":"00000","data":{"result":null},"msg":"响应成功"}}（2026-08-27 curl 复现）。
 *
 * <p>三篇客户接口文档（事件生产/发送方法/事件库举例）要点：
 * <ul>
 *   <li>消息结构 9 字段：traceId/spanId/parentId/eventCode/eventSource/eventTopic/time/key/data</li>
 *   <li>{@code key} 是业务主键**字段名**（deptId/userId 风格），非主键值</li>
 *   <li>{@code data} 仅含查询用关键标识，序列化为 JSON 字符串（网关 data 字段是 String）</li>
 *   <li>{@code eventCode}（如 BaseOssDept）经 SDK 放入 body 的 {@code eventTopic} 字段</li>
 *   <li>无父链路时 {@code parentId="0"}</li>
 * </ul>
 *
 * <p>排障回顾（2026-08-27 多次实测）：
 * <ul>
 *   <li>缺 {@code key} 字段 -> 曾怀疑网关静默丢弃，但 2026-08-27 探针实测推翻：
 *       网关对所有合规请求均返回 {@code {"data":{"result":null}}}（成功），
 *       日志中看到的 {@code data:{}} 是 fastjson 1.2.83 解析时丢弃 null 值的默认行为，
 *       <b>不是网关返回空对象</b></li>
 *   <li>{@code serviceName} 不发：客户成功样例 body 无此字段；且 SDK 的
 *       {@code ServiceRegisterProperties} 类级 {@code @ConfigurationProperties(prefix="client.register")}
 *       会把环境值（BidSystemOrgConsumer）绑定覆盖到该类型所有 bean，曾导致错误服务名上报（result=false）</li>
 * </ul>
 *
 * <p>trace 三元组写入 body（对齐客户样例），HTTP Header 同时保留
 * （SDK {@code buildHeaderParamMap} 既有行为，双写无副作用）。
 *
 * <p>结果解释：网关成功返回 {@code {"code":"00000","data":{"result":null},"msg":"响应成功"}}。
 * SDK 用 fastjson 1.2.83 解析后 {@code result:null} 被丢弃，data 变成空对象 {}。
 * 本类 {@code interpretResult} 逻辑正确：data 是 JSONObject 且 result 非 false 即成功，
 * 空 data（result=null 被丢弃后）同样判成功。仅显式 {@code result=false} 判失败。
 *
 * <p>已知 SDK HTTP 行为（字节码核实）：仅状态码 200 时解析响应体并返回；非 200 状态码
 * 与 IO 异常均被 {@code HttpClientUtil} 静默吞掉后返回 null，本类将 null 直接判为失败。
 *
 * <p>标讯事件与商机预测提醒事件共用同一总线，两者都通过本类发送。
 */
@Slf4j
final class TenderEventHttpSender {

    /** 网关 {@code EventContentReqDto.data} 声明为 String，业务载荷须序列化为 JSON 字符串。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String serverRegisterUrl;

    TenderEventHttpSender(String serverRegisterUrl) {
        this.serverRegisterUrl = serverRegisterUrl;
    }

    /**
     * 发送事件并解释总线返回结果。
     *
     * @param code     事件类型（topic/source 来自 {@link TenderEventCode}）
     * @param data     业务关键标识载荷（将序列化为 JSON 字符串上报）
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
        // 完全复刻客户手动触发成功样例（tenderId=2282）：trace 三元组进 body
        body.put("traceId", traceId);
        body.put("spanId", spanId);
        body.put("parentId", parentId);
        body.put("eventTopic", code.topic());
        body.put("eventSource", code.source());
        // 业务主键字段名（客户样例 key="tenderId"）
        body.put("key", code.dataIdField());
        // 事件产生时间（毫秒时间戳）：客户技术规格书公共消息结构标注 time 为必需字段，
        // 客户手动触发成功样例亦含 time，缺此字段网关虽受理（HTTP 200）但事件不进入投递链路
        body.put("time", System.currentTimeMillis());
        // data 必须是 JSON 字符串（EventContentReqDto.data 是 String，传对象 400 实测）
        body.put("data", serializeData(data));

        // 请求体落日志：联调对账用
        log.info("事件总线请求 eventTopic={} body={}", code.topic(), body);

        JSONObject resp = HttpClientUtil.httpPost(
                serverRegisterUrl + "/eventbus/publishEvent",
                body,
                SystemInteractiveServiceImpl.buildHeaderParamMap(trackReq),
                HttpInterfaceTypeEnum.HTTP_PUBLISH_EVENT.getValue());

        // 响应落日志。注意：SDK 内部用 fastjson 1.2.83 的 JSONObject.parseObject 解析原始响应，
        // fastjson 默认丢弃 null 值，所以网关原始 {"data":{"result":null}} 经解析后 data 变成 {}
        // （result 键因 null 被丢弃）。日志打印的 data:{} 不代表网关返回空对象，而是 fastjson 序列化结果。
        // 2026-08-27 探针实测证实：同一请求，原始 HTTP 响应=data:{"result":null}，fastjson 解析后=data:{}
        log.info("事件总线响应 eventTopic={} response={}", code.topic(), resp);

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
