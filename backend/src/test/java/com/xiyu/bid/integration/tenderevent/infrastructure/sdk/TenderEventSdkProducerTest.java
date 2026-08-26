package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ehsy.eventlibrary.clientsdk.common.req.EventTrackReq;
import com.ehsy.eventlibrary.clientsdk.common.resp.SendEventRespDto;
import com.ehsy.eventlibrary.clientsdk.systeminteraction.service.SystemInteractiveService;
import com.xiyu.bid.integration.tenderevent.application.TenderEventPublishCommand;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventCode;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("TenderEventSdkProducer - 经 SDK SystemInteractiveService 推送")
class TenderEventSdkProducerTest {

    private SystemInteractiveService sdkService;
    private TenderEventSdkProducer producer;

    @BeforeEach
    void setUp() {
        sdkService = mock(SystemInteractiveService.class);
        producer = new TenderEventSdkProducer(
                new TenderEventSdkProperties(false, "http://event-bus.local", "bid"),
                sdkService);
    }

    private TenderEventPublishCommand command() {
        return new TenderEventPublishCommand(
                TenderEventCode.BID_TENDER_CHANGE,
                new TenderEventPayload(123L, "ext-1"),
                "trace-1", "span-1", "parent-1");
    }

    private SendEventRespDto resp(Boolean result) {
        return new SendEventRespDto(result);
    }

    @Test
    @DisplayName("发送成功：topic/source/data JSON 字符串/trace 三元组按 SDK 契约传递")
    void publish_passesContractArgsToSdk() {
        when(sdkService.sendEvent(anyString(), anyString(), anyString(), any(EventTrackReq.class)))
                .thenReturn(resp(true));

        assertThat(producer.publish(command())).isTrue();

        ArgumentCaptor<EventTrackReq> trackCaptor = ArgumentCaptor.forClass(EventTrackReq.class);
        verify(sdkService).sendEvent(
                org.mockito.ArgumentMatchers.eq("BidTenderChange"),
                org.mockito.ArgumentMatchers.eq("bid"),
                org.mockito.ArgumentMatchers.eq("{\"tenderId\":123,\"externalId\":\"ext-1\"}"),
                trackCaptor.capture());
        EventTrackReq track = trackCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(track.getTraceId()).isEqualTo("trace-1");
        org.assertj.core.api.Assertions.assertThat(track.getSpanId()).isEqualTo("span-1");
        org.assertj.core.api.Assertions.assertThat(track.getParentId()).isEqualTo("parent-1");
    }

    @Test
    @DisplayName("externalId 为空 → data 字符串只含 tenderId")
    void publish_blankExternalId_omitsField() {
        when(sdkService.sendEvent(anyString(), anyString(), anyString(), any(EventTrackReq.class)))
                .thenReturn(resp(true));

        TenderEventPublishCommand cmd = new TenderEventPublishCommand(
                TenderEventCode.BID_TENDER_CHANGE,
                new TenderEventPayload(123L, "  "),
                "t", "s", "p");

        assertThat(producer.publish(cmd)).isTrue();
        verify(sdkService).sendEvent(anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq("{\"tenderId\":123}"), any(EventTrackReq.class));
    }

    @Test
    @DisplayName("SDK 返回 result=null（网关 code=00000 实测形态）→ 视为发送成功")
    void publish_nullResult_treatedAsSuccess() {
        when(sdkService.sendEvent(anyString(), anyString(), anyString(), any(EventTrackReq.class)))
                .thenReturn(resp(null));

        assertThat(producer.publish(command())).isTrue();
    }

    @Test
    @DisplayName("SDK 返回 result=false → 返回 false")
    void publish_resultFalse_returnsFalse() {
        when(sdkService.sendEvent(anyString(), anyString(), anyString(), any(EventTrackReq.class)))
                .thenReturn(resp(false));

        assertThat(producer.publish(command())).isFalse();
    }

    @Test
    @DisplayName("SDK 抛异常（如网关非 200 状态码触发 NPE）→ 返回 false（不抛异常）")
    void publish_sdkThrows_returnsFalse() {
        when(sdkService.sendEvent(anyString(), anyString(), anyString(), any(EventTrackReq.class)))
                .thenThrow(new NullPointerException("sdk non-200 quirk"));

        assertThat(producer.publish(command())).isFalse();
    }
}
