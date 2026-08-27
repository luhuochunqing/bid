package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 事件总线 wire 契约测试：真实 SDK HTTP 工具 {@code HttpClientUtil.httpPost} 打本地
 * MockWebServer，锁定请求路径、消息体结构与 trace Header，防止 SDK 升级或本方改造
 * 造成契约漂移。
 */
@DisplayName("TenderEventHttpSender wire 契约 - 真实 SDK HTTP 层")
class TenderEventHttpSenderWireTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static MockWebServer server;
    private static TenderEventHttpSender sender;

    @BeforeAll
    static void startAll() throws IOException {
        server = new MockWebServer();
        server.start();
        sender = new TenderEventHttpSender(server.url("/").toString());
    }

    @AfterAll
    static void stopAll() throws IOException {
        server.shutdown();
    }

    private static RecordedRequest takeOnlyRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(request).as("事件总线应在 5s 内收到请求").isNotNull();
        assertThat(request.getPath()).isEqualTo("/eventbus/publishEvent");
        return request;
    }

    @Test
    @DisplayName("消息体仅 SDK 契约 4 字段 + data 为 JSON 字符串 + trace 进 Header；result=true 成功")
    void wire_bodyAndHeaders_matchSdkContract() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"data\":{\"result\":true}}")
                .setHeader("Content-Type", "application/json"));

        boolean ok = sender.send(TenderEventCode.BID_TENDER_CHANGE,
                Map.of("tenderId", 123, "externalId", "ext-1"),
                "trace-1", "span-1", "parent-1");

        assertThat(ok).isTrue();
        RecordedRequest request = takeOnlyRequest();
        JsonNode body = JSON.readTree(request.getBody().readUtf8());
        // 复刻客户手动触发成功样例（tenderId=2282）：trace 三元组进 body、key=业务主键字段名、
        // 无 serviceName；data 为 JSON 字符串（EventContentReqDto.data 是 String，传对象 400 实测）
        assertThat(body.get("traceId").asText()).isEqualTo("trace-1");
        assertThat(body.get("spanId").asText()).isEqualTo("span-1");
        assertThat(body.get("parentId").asText()).isEqualTo("parent-1");
        assertThat(body.get("eventTopic").asText()).isEqualTo("BidTenderChange");
        assertThat(body.get("eventSource").asText()).isEqualTo("bid");
        assertThat(body.get("key").asText()).isEqualTo("tenderId");
        // time = 事件产生时间（毫秒时间戳），客户文档公共消息结构标注必需，手动触发成功样例亦含
        assertThat(body.get("time").asLong()).isGreaterThan(0L);
        assertThat(body.has("serviceName")).as("serviceName 不应在 body（客户样例无此字段）").isFalse();
        // data 是 JSON 字符串（EventContentReqDto.data 声明为 String）
        JsonNode data = JSON.readTree(body.get("data").asText());
        assertThat(data.get("tenderId").asInt()).isEqualTo(123);
        assertThat(data.get("externalId").asText()).isEqualTo("ext-1");

        // trace 三元组同时保留在 HTTP Header（SDK buildHeaderParamMap 既有行为，双写无副作用）
        assertThat(request.getHeader("traceId")).isEqualTo("trace-1");
        assertThat(request.getHeader("spanId")).isEqualTo("span-1");
        assertThat(request.getHeader("parentId")).isEqualTo("parent-1");
    }

    @Test
    @DisplayName("网关实测形态 code=00000 / result=null → 视为成功")
    void wire_gatewayCode00000_nullResult_success() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"code\":\"00000\",\"data\":{\"result\":null},\"msg\":\"响应成功\"}")
                .setHeader("Content-Type", "application/json"));

        boolean ok = sender.send(TenderEventCode.BID_TENDER_CHANGE,
                Map.of("tenderId", 1), "t", "s", "p");

        assertThat(ok).isTrue();
        takeOnlyRequest();
    }

    @Test
    @DisplayName("result=false → 失败")
    void wire_resultFalse_failure() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"data\":{\"result\":false}}")
                .setHeader("Content-Type", "application/json"));

        boolean ok = sender.send(TenderEventCode.BID_TENDER_CHANGE,
                Map.of("tenderId", 1), "t", "s", "p");

        assertThat(ok).isFalse();
        takeOnlyRequest();
    }

    @Test
    @DisplayName("网关非 200 状态码 → SDK httpPost 静默返回 null → 判为失败（字节码核实的真实契约）")
    void wire_non200Status_returnsFalse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        boolean ok = sender.send(TenderEventCode.BID_TENDER_CHANGE,
                Map.of("tenderId", 1), "t", "s", "p");

        assertThat(ok).isFalse();
        // 消费本测例请求，避免残留队列影响后续用例
        takeOnlyRequest();
    }
}
