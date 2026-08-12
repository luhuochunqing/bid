package com.xiyu.bid.integration.tenderevent.application;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPayload;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventStatus;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TenderEventPublishService - 标讯事件发布编排")
class TenderEventPublishServiceTest {

    private static final Executor SYNC_EXECUTOR = Runnable::run;

    @Mock private TenderEventPublishPort publisher;
    @Mock private TenderEventLogPort eventLog;
    @Mock private TenderEventPayloadMapper payloadMapper;

    private TenderEventPublishService buildService(boolean enabled) {
        return new TenderEventPublishService(publisher, eventLog, payloadMapper, SYNC_EXECUTOR, enabled);
    }

    private Tender tender(Long id, Tender.SourceType source) {
        Tender t = new Tender();
        t.setId(id);
        t.setSourceType(source);
        t.setExternalId("ext-" + id);
        return t;
    }

    @Test
    @DisplayName("功能开关关闭 → 不推送、不记录流水")
    void disabled_skipsPublishAndLog() {
        buildService(false).publishOnCreate(tender(1L, Tender.SourceType.MANUAL_SINGLE));

        verifyNoInteractions(publisher, eventLog, payloadMapper);
    }

    @Test
    @DisplayName("tender 为空 → 跳过")
    void nullTender_skips() {
        buildService(true).publishOnCreate(null);

        verifyNoInteractions(publisher, eventLog, payloadMapper);
    }

    @Test
    @DisplayName("tender id 为空 → 跳过")
    void nullTenderId_skips() {
        buildService(true).publishOnCreate(tender(null, Tender.SourceType.MANUAL_SINGLE));

        verifyNoInteractions(publisher, eventLog, payloadMapper);
    }

    @Test
    @DisplayName("CRM 回发来源 → 不推送、不记录流水")
    void crmSource_skips() {
        buildService(true).publishOnCreate(tender(1L, Tender.SourceType.CRM_OPPORTUNITY));

        verifyNoInteractions(publisher, eventLog, payloadMapper);
    }

    @Test
    @DisplayName("人工录入 → 异步推送成功并记录 SENT 流水")
    void manualSource_publishesAndLogsSent() {
        when(payloadMapper.toJson(any())).thenReturn("{\"tenderId\":1}");
        when(publisher.publish(any())).thenReturn(true);

        buildService(true).publishOnCreate(tender(1L, Tender.SourceType.MANUAL_SINGLE));

        ArgumentCaptor<TenderEventPublishCommand> cmd = ArgumentCaptor.forClass(TenderEventPublishCommand.class);
        verify(publisher).publish(cmd.capture());
        assertThat(cmd.getValue().payload()).isEqualTo(new TenderEventPayload(1L, "ext-1"));

        ArgumentCaptor<TenderEventLogRecord> log = ArgumentCaptor.forClass(TenderEventLogRecord.class);
        verify(eventLog).record(log.capture());
        assertThat(log.getValue().status()).isEqualTo(TenderEventStatus.SENT);
        assertThat(log.getValue().tenderId()).isEqualTo(1L);
        assertThat(log.getValue().eventCode()).isEqualTo("BID_TENDER_CHANGE");
        assertThat(log.getValue().eventContent()).isEqualTo("{\"tenderId\":1}");
    }

    @Test
    @DisplayName("推送返回 false → 记录 FAILED 流水")
    void publishFalse_logsFailed() {
        when(payloadMapper.toJson(any())).thenReturn("{\"tenderId\":1}");
        when(publisher.publish(any())).thenReturn(false);

        buildService(true).publishOnCreate(tender(1L, Tender.SourceType.MANUAL_SINGLE));

        ArgumentCaptor<TenderEventLogRecord> log = ArgumentCaptor.forClass(TenderEventLogRecord.class);
        verify(eventLog).record(log.capture());
        assertThat(log.getValue().status()).isEqualTo(TenderEventStatus.FAILED);
    }

    @Test
    @DisplayName("推送抛异常 → 记录 FAILED 流水并截断错误信息")
    void publishException_logsFailedWithError() {
        when(payloadMapper.toJson(any())).thenReturn("{\"tenderId\":1}");
        when(publisher.publish(any())).thenThrow(new IllegalStateException("boom"));

        buildService(true).publishOnCreate(tender(1L, Tender.SourceType.MANUAL_SINGLE));

        ArgumentCaptor<TenderEventLogRecord> log = ArgumentCaptor.forClass(TenderEventLogRecord.class);
        verify(eventLog).record(log.capture());
        assertThat(log.getValue().status()).isEqualTo(TenderEventStatus.FAILED);
        assertThat(log.getValue().errorMessage()).contains("boom");
    }
}