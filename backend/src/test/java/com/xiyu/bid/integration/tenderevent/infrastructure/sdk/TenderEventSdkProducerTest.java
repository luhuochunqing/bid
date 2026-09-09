package com.xiyu.bid.integration.tenderevent.infrastructure.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

@DisplayName("TenderEventSdkProducer - 直接调用客户 SDK sendEvent")
class TenderEventSdkProducerTest {
    private SystemInteractiveService interactiveService;
    private TenderEventSdkProducer producer;

    @BeforeEach
    void setUp() {
        interactiveService = mock(SystemInteractiveService.class);
        producer = new TenderEventSdkProducer(interactiveService);
    }

    private static TenderEventPayload payload(String externalId) {
        return new TenderEventPayload(123L, externalId, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    private static TenderEventPublishCommand command() {
        return new TenderEventPublishCommand(TenderEventCode.BID_TENDER_CHANGE, payload("ext-1"),
                "123456789012345678", "223456789012345678", "0");
    }

    @Test
    @DisplayName("发送成功：topic/source/data/track 按客户 SDK 契约传递")
    void publish_passesContractArgsDirectlyToSdk() {
        when(interactiveService.sendEvent(eq("BidTenderChange"), eq("bid"), any(), any()))
                .thenReturn(new SendEventRespDto(true));
        assertThat(producer.publish(command())).isTrue();

        ArgumentCaptor<String> data = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EventTrackReq> track = ArgumentCaptor.forClass(EventTrackReq.class);
        verify(interactiveService).sendEvent(eq("BidTenderChange"), eq("bid"), data.capture(), track.capture());
        assertThat(data.getValue()).isEqualTo("{\"tenderId\":123,\"externalId\":\"ext-1\"}");
        assertThat(track.getValue().getTraceId()).isEqualTo("123456789012345678");
        assertThat(track.getValue().getSpanId()).isEqualTo("223456789012345678");
        assertThat(track.getValue().getParentId()).isEqualTo("0");
    }

    @Test
    @DisplayName("SDK result=null 表示已受理，返回成功")
    void publish_nullResult_returnsTrue() {
        when(interactiveService.sendEvent(any(), any(), any(), any())).thenReturn(new SendEventRespDto(null));
        assertThat(producer.publish(command())).isTrue();
    }

    @Test
    @DisplayName("SDK result=false、空响应或异常均返回失败")
    void publish_failureShapes_returnFalse() {
        when(interactiveService.sendEvent(any(), any(), any(), any()))
                .thenReturn(new SendEventRespDto(false))
                .thenReturn((SendEventRespDto) null)
                .thenThrow(new RuntimeException("gateway unavailable"));
        assertThat(producer.publish(command())).isFalse();
        assertThat(producer.publish(command())).isFalse();
        assertThat(producer.publish(command())).isFalse();
    }
}
