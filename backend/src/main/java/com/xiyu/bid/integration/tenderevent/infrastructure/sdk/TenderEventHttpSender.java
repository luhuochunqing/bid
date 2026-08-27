package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.alibaba.fastjson.JSONObject;
import com.ehsy.eventlibrary.clientsdk.common.enums.HttpInterfaceTypeEnum;
import com.ehsy.eventlibrary.clientsdk.common.req.EventTrackReq;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.impl.SystemInteractiveServiceImpl;
import com.ehsy.eventlibrary.clientsdk.util.HttpClientUtil;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 西域事件总线发送器（基础设施层 WithEffect，包内共享）。
 *
 * <p>body 按客户手动触发成功样例（2026-08-27，tenderId=2282）**完全复刻**：
 * <pre>{traceId, spanId, parentId, eventSource, eventTopic, key, data}</pre>
 * 客户成功样例（执行成功）：
 * <pre>
 * {
 *   "traceId": "748104276040151040",
 *   "spanId": "s748104276040151041",
 *   "data": {"tenderId": "2282"},      // 对象形态，非字符串
 *   "eventSource": "bid",
 *   "eventTopic": "BidTenderChange",
 *   "key": "tenderId",                 // 业务主键字段名（部门事件=deptId、员工事件=userId）
 *   "parentId": "0"
 * }
 * </pre>
 *
 * <p>三篇客户接口文档（事件生产/发送方法/事件库举例）要点：
 * <ul>
 *   <li>消息结构 9 字段：traceId/spanId/parentId/eventCode/eventSource/eventTopic/time/key/data</li>
 *   <li>{@code key} 是业务主键**字段名**（deptId/userId 风格），非主键值</li>
 *   <li>{@code data} 仅含查询用关键标识；SDK 通道序列化为字符串，手动触发样例为对象</li>
 *   <li>{@code eventCode}（如 BaseOssDept）经 SDK 放入 body 的 {@code eventTopic} 字段</li>
 *   <li>无父链路时 {@code parentId="0"}</li>
 * </ul>
 *
 * <p>排障回顾（2026-08-27 多次实测）：
 * <ul>
 *   <li>缺 {@code key} 字段 → 网关静默丢弃（data:{}）；补 key 后仍需验证 trace 位置/data 形态</li>
 *   <li>{@code serviceName} 不发：客户成功样例 body 无此字段；且 SDK 的
 *       {@code ServiceRegisterProperties} 类级 {@code @ConfigurationProperties(prefix="client.register")}
 *       会把环境值（BidSystemOrgConsumer）绑定覆盖到该类型所有 bean，曾导致错误服务名上报（result=false）</li>
 * </ul>
 *
 * <p>trace 三元组写入 body（对齐客户样例），HTTP Header 同时保留
 * （SDK {@code buildHeaderParamMap} 既有行为，双写无副作用）。
 *
 * <p>结果解释：真实网关成功时返回外层 {@code code=="00000"} 且 {@code data.result} 非 false
 * （实测形态含 result=null 与 data:{}），仅显式 result=false 判为失败；响应缺失 data 字段
 * 或 data 非对象时按失败处理（SDK 原生路径会抛 NPE/JSONException，经调用方降级后净效果一致）。
 *
 * <p>已知 SDK HTTP 行为（字节码核实）：仅状态码 200 时解析响应体并返回；非 200 状态码
 * 与 IO 异常均被 {@code HttpClientUtil} 静默吞掉后返回 null，本类将 null 直接判为失败。
 *
 * <p>标讯事件与商机预测提醒事件共用同一总线，两者都通过本类发送。
 */
@Slf4j
final class TenderEventHttpSender {

    private final String serverRegisterUrl;

    TenderEventHttpSender(String serverRegisterUrl) {
        this.serverRegisterUrl = serverRegisterUrl;
    }

    /**
     * 发送事件并解释总线返回结果。
     *
     * @param code     事件类型（topic/source 来自 {@link TenderEventCode}）
     * @param data     业务关键标识载荷（直接作对象发送）
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
        // 完全复刻客户手动触发成功样例（tenderId=2282）：trace 三元组进 body，data 为对象
        body.put("traceId", traceId);
        body.put("spanId", spanId);
        body.put("parentId", parentId);
        body.put("eventTopic", code.topic());
        body.put("eventSource", code.source());
        // 业务主键字段名（客户样例 key="tenderId"），缺此字段网关静默丢弃（data:{} 实测）
        body.put("key", code.dataIdField());
        body.put("data", data);

        // 请求体落日志：联调对账用
        log.info("事件总线请求 eventTopic={} body={}", code.topic(), body);

        JSONObject resp = HttpClientUtil.httpPost(
                serverRegisterUrl + "/eventbus/publishEvent",
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
}
