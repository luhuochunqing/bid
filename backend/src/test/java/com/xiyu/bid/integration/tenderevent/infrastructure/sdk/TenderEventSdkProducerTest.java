package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import com.alibaba.fastjson.JSONObject;
import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRegisterProperties;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.impl.SystemInteractiveServiceImpl;
import com.ehsy.eventlibrary.clientsdk.util.HttpClientUtil;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishCommand;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPayload;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@DisplayName("TenderEventSdkProducer - 经 TenderEventSdkSender（SDK sendEvent 通道）推送")
class TenderEventSdkProducerTest {

    private static final String PUBLISH_URL = "http://event-bus.local/eventbus/publishEvent";

    private MockedStatic<HttpClientUtil> httpClient;
    private TenderEventSdkProducer producer;

    @BeforeEach
    void setUp() {
        httpClient = mockStatic(HttpClientUtil.class);
        producer = new TenderEventSdkProducer(new TenderEventSdkSender("http://event-bus.local", "bid"));
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
    @DisplayName("发送成功：URL/SDK 契约 4 字段 body（serviceName 必带）/trace Header 按 SDK 契约传递")
    void publish_passesContractArgsToSdkLayer() {
        httpClient.when(() -> HttpClientUtil.httpPost(anyString(), any(), any(), anyString()))
                .thenReturn(JSONObject.parseObject("{\"data\":{\"result\":true}}"));

        assertThat(producer.publish(command())).isTrue();

        ArgumentCaptor<JSONObject> bodyCaptor = ArgumentCaptor.forClass(JSONObject.class);
        ArgumentCaptor<Map<String, String>> headers = headerCaptor();
        httpClient.verify(() -> HttpClientUtil.httpPost(anyString(), bodyCaptor.capture(),
                headers.capture(), anyString()));
        JSONObject body = bodyCaptor.getValue();
        // SDK sendEvent 组装的 body 4 字段（反编译核实）：serviceName 来自 SDK 配置、
        // eventTopic=eventCode、eventSource、data=eventContent（JSON 字符串）
        assertThat(body.getString("serviceName")).isEqualTo("bid");
        assertThat(body.getString("eventTopic")).isEqualTo("BidTenderChange");
        assertThat(body.getString("eventSource")).isEqualTo("bid");
        assertThat(body.getString("data")).isEqualTo("{\"tenderId\":123,\"externalId\":\"ext-1\"}");
        assertThat(body.getString("key")).as("key 必须为业务字段名 tenderId").isEqualTo("tenderId");
        assertThat(body.getLong("time")).as("time 为毫秒时间戳").isPositive();
        assertThat(body.getString("traceId")).isEqualTo("trace-1");
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
    @DisplayName("SDK 层抛异常（如网关非 200 → sendEvent NPE）→ 返回 false（不抛异常）")
    void publish_sdkThrows_returnsFalse() {
        httpClient.when(() -> HttpClientUtil.httpPost(anyString(), any(), any(), anyString()))
                .thenThrow(new RuntimeException("sdk non-200 quirk"));

        assertThat(producer.publish(command())).isFalse();
    }
}
