package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRegisterProperties;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.impl.SystemInteractiveServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 事件总线 wire 契约测试：真实 SDK {@code SystemInteractiveServiceImpl.sendEvent} 打本地
 * MockWebServer，锁定 SDK 组装的请求路径、消息体结构与 trace Header，防止 SDK 升级或
 * 本方改造造成契约漂移。
 *
 * <p>SDK sendEvent 组装的 body 固定 4 字段 {@code {serviceName, eventTopic, eventSource, data}}
 * （2026-08-27 客户反编译截图 + jar 字节码双重核实），serviceName 是网关投递路由的
 * 必带字段；key/time/trace 不进 body。
 */
@DisplayName("TenderEventSdkSender wire 契约 - 真实 SDK sendEvent 通道")
class TenderEventSdkSenderWireTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static MockWebServer server;
    private static TenderEventSdkSender sender;

    @BeforeAll
    static void startAll() throws Exception {
        server = new MockWebServer();
        server.start();
        sender = new TenderEventSdkSender(newSdkService(server.url("/").toString()), "bid");
    }

    @AfterAll
    static void stopAll() throws IOException {
        server.shutdown();
    }

    /** 手工构造 SDK 服务实例：生产环境由 Spring 注入 {@code ServiceRegisterProperties}，
     *  测试里反射注入同构配置（SDK 实现为私有字段注入，无公开 setter）。 */
    private static SystemInteractiveService newSdkService(String serverRegisterUrl) throws Exception {
        ServiceRegisterProperties sdkProps = new ServiceRegisterProperties();
        sdkProps.setServiceName("bid");
        sdkProps.setServerRegisterUrl(serverRegisterUrl);
        SystemInteractiveServiceImpl service = new SystemInteractiveServiceImpl();
        Field field = SystemInteractiveServiceImpl.class.getDeclaredField("clientSdkConfig");
        field.setAccessible(true);
        field.set(service, sdkProps);
        return service;
    }

    private static RecordedRequest takeOnlyRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(request).as("事件总线应在 5s 内收到请求").isNotNull();
        assertThat(request.getPath()).isEqualTo("/eventbus/publishEvent");
        return request;
    }

    @Test
    @DisplayName("SDK 契约 4 字段 body（serviceName 必带）+ data 为 JSON 字符串 + trace 进 Header；result=true 成功")
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
        // SDK sendEvent 组装的 body 只有 4 字段（反编译核实）：serviceName 来自 SDK 配置、
        // eventTopic=eventCode、eventSource、data=eventContent（JSON 字符串）
        assertThat(body.get("serviceName").asText()).isEqualTo("bid");
        assertThat(body.get("eventTopic").asText()).isEqualTo("BidTenderChange");
        assertThat(body.get("eventSource").asText()).isEqualTo("bid");
        JsonNode data = JSON.readTree(body.get("data").asText());
        assertThat(data.get("tenderId").asInt()).isEqualTo(123);
        assertThat(data.get("externalId").asText()).isEqualTo("ext-1");
        // key/time/trace 不是 SDK wire 契约字段（事件库内部存储形态才有），不得进 body
        assertThat(body.has("key")).as("SDK 契约 body 无 key").isFalse();
        assertThat(body.has("time")).as("SDK 契约 body 无 time").isFalse();
        assertThat(body.has("traceId")).as("SDK 契约 body 无 traceId").isFalse();

        // trace 三元组经 EventTrackReq 写入 HTTP Header（SDK buildHeaderParamMap）
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
    @DisplayName("网关非 200 状态码 → SDK httpPost 静默 null → sendEvent NPE → 兜底判失败（字节码核实的真实契约）")
    void wire_non200Status_returnsFalse() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        boolean ok = sender.send(TenderEventCode.BID_TENDER_CHANGE,
                Map.of("tenderId", 1), "t", "s", "p");

        assertThat(ok).isFalse();
        // 消费本测例请求，避免残留队列影响后续用例
        takeOnlyRequest();
    }
}
