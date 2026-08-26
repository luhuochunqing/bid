package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRegisterProperties;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.impl.SystemInteractiveServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 事件总线 wire 契约测试：真实 SDK {@link SystemInteractiveServiceImpl} 打本地 MockWebServer，
 * 锁定请求路径、消息体结构与 trace Header，防止 SDK 升级或本方改造造成契约漂移。
 */
@DisplayName("TenderEventHttpSender wire 契约 - 真实 SDK 实现")
class TenderEventHttpSenderWireTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static MockWebServer server;
    private static AnnotationConfigApplicationContext context;
    private static TenderEventHttpSender sender;

    @Configuration
    @Import(SystemInteractiveServiceImpl.class)
    static class TestConfig {
        @Bean
        ServiceRegisterProperties serviceRegisterProperties() {
            ServiceRegisterProperties props = new ServiceRegisterProperties();
            props.setServiceName("bid");
            props.setServerRegisterUrl(server.url("/").toString());
            return props;
        }
    }

    @BeforeAll
    static void startAll() throws IOException {
        server = new MockWebServer();
        server.start();
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        sender = new TenderEventHttpSender(context.getBean(SystemInteractiveService.class));
    }

    @AfterAll
    static void stopAll() throws IOException {
        context.close();
        server.shutdown();
    }

    private static RecordedRequest takeOnlyRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(request).as("事件总线应在 5s 内收到请求").isNotNull();
        assertThat(request.getPath()).isEqualTo("/eventbus/publishEvent");
        return request;
    }

    @Test
    @DisplayName("消息体结构 + data 为 JSON 字符串 + trace 进 Header；result=true 成功")
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
        assertThat(body.get("serviceName").asText()).isEqualTo("bid");
        assertThat(body.get("eventTopic").asText()).isEqualTo("BidTenderChange");
        assertThat(body.get("eventSource").asText()).isEqualTo("bid");
        // data 必须是 JSON 字符串（网关 EventContentReqDto.data 为 String，传对象会被 400 拒收）
        JsonNode data = JSON.readTree(body.get("data").asText());
        assertThat(data.get("tenderId").asInt()).isEqualTo(123);
        assertThat(data.get("externalId").asText()).isEqualTo("ext-1");

        assertThat(request.getHeader("traceId")).isEqualTo("trace-1");
        assertThat(request.getHeader("spanId")).isEqualTo("span-1");
        assertThat(request.getHeader("parentId")).isEqualTo("parent-1");
    }

    @Test
    @DisplayName("网关实测形态 code=00000 / result=null → SDK 返回 result=null，视为成功")
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
    @DisplayName("网关非 200 状态码 → SDK 内部抛 RuntimeException（已知 SDK 行为，调用方降级）")
    void wire_non200Status_sdkThrows() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThatThrownBy(() -> sender.send(TenderEventCode.BID_TENDER_CHANGE,
                Map.of("tenderId", 1), "t", "s", "p"))
                .isInstanceOf(RuntimeException.class);
        // 消费本测例请求，避免残留队列影响后续用例
        takeOnlyRequest();
    }
}
