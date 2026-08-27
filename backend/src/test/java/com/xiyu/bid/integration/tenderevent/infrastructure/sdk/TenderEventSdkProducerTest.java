package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import com.alibaba.fastjson.JSONObject;
import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRegisterProperties;
import com.ehsy.eventlibrary.clientsdk.util.HttpClientUtil;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishCommand;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPayload;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@DisplayName("TenderEventSdkProducer - 经 TenderEventHttpSender 推送")
class TenderEventSdkProducerTest {

    private static final String PUBLISH_URL = "http://event-bus.local/eventbus/publishEvent";

    private MockedStatic<HttpClientUtil> httpClient;
    private TenderEventSdkProducer producer;

    @BeforeEach
    void setUp() {
        httpClient = mockStatic(HttpClientUtil.class);
        ServiceRegisterProperties registerProperties = new ServiceRegisterProperties();
        registerProperties.setServiceName("bid");
        registerProperties.setServerRegisterUrl("http://event-bus.local");
        producer = new TenderEventSdkProducer(
                new TenderEventSdkProperties(false, "http://event-bus.local", "bid"),
                registerProperties);
    }

    @AfterEach
    void tearDown() {
        httpClient.close();
    }

    private TenderEventPublishCommand command() {
        return new TenderEventPublishCommand(
                TenderEventCode.BID_TENDER_CHANGE,
                new TenderEventPayload(123L, "ext-1"),
                "trace-1", "span-1", "parent-1");
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, String>> headerCaptor() {
        return ArgumentCaptor.forClass(Map.class);
    }

    @Test
    @DisplayName("发送成功：URL/SDK 契约 4 字段 body/trace Header 按 SDK 契约传递")
    void publish_passesContractArgsToHttpLayer() {
        httpClient.when(() -> HttpClientUtil.httpPost(anyString(), any(), any(), anyString()))
                .thenReturn(JSONObject.parseObject("{\"data\":{\"result\":true}}"));

        assertThat(producer.publish(command())).isTrue();

        ArgumentCaptor<JSONObject> bodyCaptor = ArgumentCaptor.forClass(JSONObject.class);
        ArgumentCaptor<Map<String, String>> headers = headerCaptor();
        httpClient.verify(() -> HttpClientUtil.httpPost(eq(PUBLISH_URL), bodyCaptor.capture(),
                headers.capture(), eq("publishEvent")));
        JSONObject body = bodyCaptor.getValue();
        // SDK 契约仅 4 字段：serviceName / eventTopic / eventSource / data
        // 消息结构内部字段（eventCode/time/key/traceId/spanId/parentId）由网关自身填，不可塞 body
        assertThat(body.getString("serviceName")).isEqualTo("bid");
        assertThat(body.getString("eventTopic")).isEqualTo("BidTenderChange");
        assertThat(body.getString("eventSource")).isEqualTo("bid");
        assertThat(body.getString("data")).isEqualTo("{\"tenderId\":123,\"externalId\":\"ext-1\"}");
        assertThat(body.containsKey("eventCode")).as("eventCode 不应在 body").isFalse();
        assertThat(body.containsKey("time")).as("time 不应在 body").isFalse();
        assertThat(body.containsKey("key")).as("key 不应在 body").isFalse();
        assertThat(body.containsKey("traceId")).as("traceId 不应在 body").isFalse();
        assertThat(body.containsKey("spanId")).as("spanId 不应在 body").isFalse();
        assertThat(body.containsKey("parentId")).as("parentId 不应在 body").isFalse();
        assertThat(headers.getValue())
                .containsEntry("traceId", "trace-1")
                .containsEntry("spanId", "span-1")
                .containsEntry("parentId", "parent-1");
    }

    @Test
    @DisplayName("externalId 为空 → data 字符串只含 tenderId")
    void publish_blankExternalId_omitsField() {
        httpClient.when(() -> HttpClientUtil.httpPost(anyString(), any(), any(), anyString()))
                .thenReturn(JSONObject.parseObject("{\"data\":{\"result\":true}}"));

        TenderEventPublishCommand cmd = new TenderEventPublishCommand(
                TenderEventCode.BID_TENDER_CHANGE,
                new TenderEventPayload(123L, "  "),
                "t", "s", "p");

        assertThat(producer.publish(cmd)).isTrue();
        ArgumentCaptor<JSONObject> bodyCaptor = ArgumentCaptor.forClass(JSONObject.class);
        httpClient.verify(() -> HttpClientUtil.httpPost(anyString(), bodyCaptor.capture(),
                any(), anyString()));
        assertThat(bodyCaptor.getValue().getString("data")).isEqualTo("{\"tenderId\":123}");
    }

    @Test
    @DisplayName("网关实测形态 code=00000 / result=null → 视为发送成功")
    void publish_nullResult_treatedAsSuccess() {
        httpClient.when(() -> HttpClientUtil.httpPost(anyString(), any(), any(), anyString()))
                .thenReturn(JSONObject.parseObject(
                        "{\"code\":\"00000\",\"data\":{\"result\":null},\"msg\":\"响应成功\"}"));

        assertThat(producer.publish(command())).isTrue();
    }

    @Test
    @DisplayName("网关返回 result=false → 返回 false")
    void publish_resultFalse_returnsFalse() {
        httpClient.when(() -> HttpClientUtil.httpPost(anyString(), any(), any(), anyString()))
                .thenReturn(JSONObject.parseObject(
                        "{\"code\":\"B20001\",\"msg\":\"topic未注册\",\"data\":{\"result\":false}}"));

        assertThat(producer.publish(command())).isFalse();
    }

    @Test
    @DisplayName("HTTP 层抛异常（如网关非 200 状态码）→ 返回 false（不抛异常）")
    void publish_httpThrows_returnsFalse() {
        httpClient.when(() -> HttpClientUtil.httpPost(anyString(), any(), any(), anyString()))
                .thenThrow(new RuntimeException("sdk non-200 quirk"));

        assertThat(producer.publish(command())).isFalse();
    }
}
