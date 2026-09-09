package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ehsy.eventlibrary.clientsdk.common.req.EventTrackReq;
import com.ehsy.eventlibrary.clientsdk.common.resp.SendEventRespDto;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService;
import com.xiyu.bid.config.TraceConstants;
import com.xiyu.bid.integration.tenderevent.domain.OpportunityPredictionEventPayload;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

@DisplayName("OpportunityPredictionEventPublisher - 直接调用客户 SDK")
class OpportunityPredictionEventPublisherTest {
    private SystemInteractiveService interactiveService;

    @BeforeEach
    void setUp() {
        interactiveService = mock(SystemInteractiveService.class);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private OpportunityPredictionEventPublisher publisher(boolean enabled) {
        TenderEventSdkProperties props = new TenderEventSdkProperties(enabled, "http://127.0.0.1:8080", "bid");
        return new OpportunityPredictionEventPublisher(props, interactiveService);
    }

    private static OpportunityPredictionEventPayload fullPayload() {
        return new OpportunityPredictionEventPayload(1L, "测试项目", "WON", "客户A", "张三", "LOCAL_SOE",
                "2025-08-15", new BigDecimal("3"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("全字段按 JSON 字符串传给 sendEvent，枚举转中文")
    void publish_fullPayload_passesSerializedDataToSdk() {
        when(interactiveService.sendEvent(any(), any(), any(), any())).thenReturn(new SendEventRespDto(true));
        assertThat(publisher(true).publish(fullPayload())).isTrue();

        ArgumentCaptor<String> data = ArgumentCaptor.forClass(String.class);
        verify(interactiveService).sendEvent(eq("OpportunityPrediction"), eq("bid"), data.capture(), any());
        assertThat(data.getValue()).isEqualTo(
                "{\"projectId\":1,\"projectName\":\"测试项目\",\"resultType\":\"已中标\",\"customer\":\"客户A\","
                        + "\"projectLeaderName\":\"张三\",\"customerType\":\"地方国企\",\"bidOpenTime\":\"2025-08-15\","
                        + "\"servicePeriodYears\":3,\"servicePeriodEndDate\":\"2026-06-01\","
                        + "\"predictedBidDate\":\"2026-03-01\"}");
    }

    @Test
    @DisplayName("复用不超过18位的非空 MDC traceId")
    void publish_reusesMdcTraceWithin18Characters() {
        when(interactiveService.sendEvent(any(), any(), any(), any())).thenReturn(new SendEventRespDto(true));
        MDC.put(TraceConstants.MDC_TRACE_KEY, "trace-123");
        assertThat(publisher(true).publish(fullPayload())).isTrue();

        ArgumentCaptor<EventTrackReq> track = ArgumentCaptor.forClass(EventTrackReq.class);
        verify(interactiveService).sendEvent(any(), any(), any(), track.capture());
        assertThat(track.getValue().getTraceId()).isEqualTo("trace-123");
        assertThat(track.getValue().getSpanId()).matches("^s[0-9a-z]+$");
        assertThat(track.getValue().getParentId()).isEqualTo("0");
    }

    @Test
    @DisplayName("超过18位的 MDC traceId 改用不超过18位的 t+base36 雪花 ID")
    void publish_oversizedMdcTrace_generatesTypedTraceWithin18Characters() {
        when(interactiveService.sendEvent(any(), any(), any(), any())).thenReturn(new SendEventRespDto(true));
        MDC.put(TraceConstants.MDC_TRACE_KEY, "1234567890123456789");
        assertThat(publisher(true).publish(fullPayload())).isTrue();

        ArgumentCaptor<EventTrackReq> track = ArgumentCaptor.forClass(EventTrackReq.class);
        verify(interactiveService).sendEvent(any(), any(), any(), track.capture());
        assertThat(track.getValue().getTraceId()).matches("^t[0-9a-z]+$");
        assertThat(track.getValue().getTraceId().length()).isLessThanOrEqualTo(18);
        assertThat(track.getValue().getParentId()).isEqualTo("0");
    }

    @Test
    @DisplayName("SDK result=null 表示已受理，返回成功")
    void publish_nullResult_returnsTrue() {
        when(interactiveService.sendEvent(any(), any(), any(), any())).thenReturn(new SendEventRespDto(null));
        assertThat(publisher(true).publish(fullPayload())).isTrue();
    }

    @Test
    @DisplayName("SDK result=false、空响应或运行时异常均返回失败")
    void publish_failureShapes_returnFalse() {
        when(interactiveService.sendEvent(any(), any(), any(), any()))
                .thenReturn(new SendEventRespDto(false))
                .thenReturn((SendEventRespDto) null)
                .thenThrow(new RuntimeException("gateway unavailable"));
        OpportunityPredictionEventPublisher publisher = publisher(true);
        assertThat(publisher.publish(fullPayload())).isFalse();
        assertThat(publisher.publish(fullPayload())).isFalse();
        assertThat(publisher.publish(fullPayload())).isFalse();
    }

    @Test
    @DisplayName("开关关闭时不调用 SDK")
    void publish_disabled_skipsSend() {
        assertThat(publisher(false).publish(fullPayload())).isFalse();
        verify(interactiveService, never()).sendEvent(any(), any(), any(), any());
    }
}
