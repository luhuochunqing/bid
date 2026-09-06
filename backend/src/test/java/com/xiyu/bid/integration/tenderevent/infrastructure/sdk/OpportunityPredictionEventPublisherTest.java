package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import com.alibaba.fastjson.JSONObject;
import com.ehsy.eventlibrary.clientsdk.config.properties.ServiceRegisterProperties;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.impl.SystemInteractiveServiceImpl;
import com.ehsy.eventlibrary.clientsdk.util.HttpClientUtil;
import com.xiyu.bid.integration.tenderevent.domain.OpportunityPredictionEventPayload;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.slf4j.MDC;

/**
 * 商机预测事件发布器测试：经真实 {@link TenderEventSdkSender}（mock SDK 底层 HTTP）验证
 * data 序列化（10 字段 / 中文映射 / null 省略）、链路 ID 雪花约束、开关跳过。
 *
 * <p>与 {@code TenderEventSdkProducerTest} 同一 mock 模式，填补商机侧 {@code buildData}
 * 此前无直接单测的缺口（标讯侧 toData 已有 TenderEventPayloadMapperTest 覆盖）。
 */
@DisplayName("OpportunityPredictionEventPublisher - 商机事件 data 序列化与链路 ID")
class OpportunityPredictionEventPublisherTest {

    private MockedStatic<HttpClientUtil> httpClient;

    @BeforeEach
    void setUp() {
        httpClient = mockStatic(HttpClientUtil.class);
        MDC.clear(); // 确保 currentTraceId 走雪花分支（无上游 traceId 复用）
    }

    @AfterEach
    void tearDown() {
        httpClient.close();
    }

    private OpportunityPredictionEventPublisher publisher(boolean enabled) throws Exception {
        TenderEventSdkProperties props =
                new TenderEventSdkProperties(enabled, "http://127.0.0.1:8080", "bid");
        TenderEventSdkSender sender = new TenderEventSdkSender(newSdkService(), "bid");
        return new OpportunityPredictionEventPublisher(props, sender);
    }

    private static SystemInteractiveService newSdkService() throws Exception {
        ServiceRegisterProperties sdkProps = new ServiceRegisterProperties();
        sdkProps.setServiceName("bid");
        sdkProps.setServerRegisterUrl("http://127.0.0.1:8080");
        SystemInteractiveServiceImpl service = new SystemInteractiveServiceImpl();
        Field field = SystemInteractiveServiceImpl.class.getDeclaredField("clientSdkConfig");
        field.setAccessible(true);
        field.set(service, sdkProps);
        return service;
    }

    private void stubHttpOk() {
        httpClient.when(() -> HttpClientUtil.httpPost(anyString(), any(), any(), anyString()))
                .thenReturn(JSONObject.parseObject("{\"data\":{\"result\":true}}"));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, String>> headerCaptor() {
        return ArgumentCaptor.forClass(Map.class);
    }

    private static OpportunityPredictionEventPayload fullPayload() {
        return new OpportunityPredictionEventPayload(
                1L, "测试项目", "WON", "客户A", "张三", "LOCAL_SOE",
                "2025-08-15", new BigDecimal("3"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("全字段：10 字段按序序列化，resultType/customerType 转中文")
    void publish_fullPayload_serializesAllFieldsWithChineseLabels() throws Exception {
        stubHttpOk();

        assertThat(publisher(true).publish(fullPayload())).isTrue();

        ArgumentCaptor<JSONObject> bodyCaptor = ArgumentCaptor.forClass(JSONObject.class);
        httpClient.verify(() -> HttpClientUtil.httpPost(anyString(), bodyCaptor.capture(), any(), anyString()));
        assertThat(bodyCaptor.getValue().getString("data")).isEqualTo(
                "{\"projectId\":1,\"projectName\":\"测试项目\",\"resultType\":\"已中标\",\"customer\":\"客户A\","
                        + "\"projectLeaderName\":\"张三\",\"customerType\":\"地方国企\",\"bidOpenTime\":\"2025-08-15\","
                        + "\"servicePeriodYears\":3,\"servicePeriodEndDate\":\"2026-06-01\","
                        + "\"predictedBidDate\":\"2026-03-01\"}");
    }

    @Test
    @DisplayName("可选字段为 null：data 省略，仅保留 projectId 与非空字段")
    void publish_nullOptionalFields_omitsThem() throws Exception {
        stubHttpOk();
        OpportunityPredictionEventPayload payload = new OpportunityPredictionEventPayload(
                2L, null, null, "客户B", null, null, null, null, null, LocalDate.of(2026, 3, 1));

        assertThat(publisher(true).publish(payload)).isTrue();

        ArgumentCaptor<JSONObject> bodyCaptor = ArgumentCaptor.forClass(JSONObject.class);
        httpClient.verify(() -> HttpClientUtil.httpPost(anyString(), bodyCaptor.capture(), any(), anyString()));
        assertThat(bodyCaptor.getValue().getString("data"))
                .isEqualTo("{\"projectId\":2,\"customer\":\"客户B\",\"predictedBidDate\":\"2026-03-01\"}");
    }

    @Test
    @DisplayName("链路 ID：traceId/spanId 为雪花 ID（t/s 前缀且 ≤19 位），满足事件库 varchar(19)")
    void publish_traceAndSpanIds_areSnowflakeWithinVarchar19() throws Exception {
        stubHttpOk();

        assertThat(publisher(true).publish(fullPayload())).isTrue();

        ArgumentCaptor<Map<String, String>> headers = headerCaptor();
        httpClient.verify(() -> HttpClientUtil.httpPost(anyString(), any(), headers.capture(), anyString()));
        Map<String, String> h = headers.getValue();
        assertThat(h.get("traceId")).startsWith("t");
        assertThat(h.get("traceId").length()).isLessThanOrEqualTo(19);
        assertThat(h.get("spanId")).startsWith("s");
        assertThat(h.get("spanId").length()).isLessThanOrEqualTo(19);
    }

    @Test
    @DisplayName("开关关闭：不发送、返回 false")
    void publish_disabled_skipsSend() throws Exception {
        assertThat(publisher(false).publish(fullPayload())).isFalse();

        httpClient.verify(() -> HttpClientUtil.httpPost(anyString(), any(), any(), anyString()), never());
    }
}
