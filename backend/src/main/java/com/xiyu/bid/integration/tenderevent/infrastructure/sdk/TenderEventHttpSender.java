package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 西域事件总线 HTTP 发送器（基础设施层 WithEffect，包内共享）。
 *
 * <p>统一 {@code POST {serverRegisterUrl}/eventbus/publishEvent} 的直连行为：
 * 消息体 {@code {serviceName, eventTopic, eventSource, data}}、响应 {@code data.result}
 * 解析、链路追踪头（traceId/spanId/parentId）写入 HTTP Header。
 *
 * <p>标讯事件与商机预测提醒事件共用同一总线，两者都通过本类发送，
 * 避免同一 wire 契约在多处重复。网络异常向上抛，由调用方按上下文记录日志并降级。
 */
final class TenderEventHttpSender {

    /** 事件总线 {@code EventContentReqDto.data} 声明为 String，须将业务载荷序列化为 JSON 字符串上报。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final TenderEventSdkProperties properties;

    TenderEventHttpSender(RestClient restClient, TenderEventSdkProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** 默认 RestClient：3s 连接超时 + 5s 读超时，指向事件总线根地址。 */
    static RestClient defaultRestClient(TenderEventSdkProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder()
                .baseUrl(properties.serverRegisterUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 发送事件并解析总线返回结果。
     *
     * @param code     事件类型（topic/source 来自 {@link TenderEventCode}）
     * @param data     业务关键标识载荷
     * @param traceId  链路追踪 traceId
     * @param spanId   链路追踪 spanId
     * @param parentId 链路追踪 parentId
     * @return 总线返回 {@code data.result=true} 时为 true
     */
    boolean send(TenderEventCode code, Map<String, Object> data,
                 String traceId, String spanId, String parentId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceName", properties.serviceName());
        body.put("eventTopic", code.topic());
        body.put("eventSource", code.source());
        body.put("data", serializeData(data));

        JsonNode response = restClient.post()
                .uri("/eventbus/publishEvent")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> applyTraceHeaders(headers, traceId, spanId, parentId))
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return response != null && response.path("data").path("result").asBoolean(false);
    }

    private static void applyTraceHeaders(HttpHeaders headers, String traceId, String spanId, String parentId) {
        headers.add("traceId", traceId);
        headers.add("spanId", spanId);
        headers.add("parentId", parentId);
    }

    private static String serializeData(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化事件总线 data 载荷", e);
        }
    }
}
