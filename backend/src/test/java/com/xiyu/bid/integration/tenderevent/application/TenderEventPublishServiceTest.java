package com.xiyu.bid.integration.tenderevent.application;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventPayload;
import com.xiyu.bid.integration.tenderevent.domain.TenderEventStatus;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

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
        t.setTitle("测试标讯" + id);
        t.setPurchaserName("招标主体X");
        t.setRegion("上海");
        t.setRegistrationDeadline(LocalDateTime.of(2026, 9, 10, 17, 0));
        t.setBidOpeningTime(LocalDateTime.of(2026, 9, 15, 9, 30));
        t.setCustomerType("LOCAL_SOE");
        t.setPriority("A");
        t.setProjectType("OFFICE");
        t.setContactName("张三");
        t.setContactPhone("13800000000");
        t.setContactTel("021-12345678");
        t.setContactMail("zhangsan@example.com");
        t.setContactNature("客户");
        t.setContactName2("李四");
        t.setContactPhone2("13900000000");
        t.setContactTel2("021-87654321");
        t.setContactMail2("lisi@example.com");
        t.setContactNature2("代理");
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
        TenderEventPayload payload = cmd.getValue().payload();
        assertThat(payload.tenderId()).isEqualTo(1L);
        assertThat(payload.externalId()).isEqualTo("ext-1");
        assertThat(payload.title()).isEqualTo("测试标讯1");
        assertThat(payload.purchaserName()).isEqualTo("招标主体X");
        assertThat(payload.region()).isEqualTo("上海");
        // payload 存后端枚举码，中文映射发生在序列化层（TenderEventPayloadMapper.toData）
        assertThat(payload.customerType()).isEqualTo("LOCAL_SOE");
        assertThat(payload.priority()).isEqualTo("A");
        assertThat(payload.projectType()).isEqualTo("OFFICE");
        assertThat(payload.contactName()).isEqualTo("张三");
        assertThat(payload.contactNature()).isEqualTo("客户");
        assertThat(payload.contactName2()).isEqualTo("李四");
        assertThat(payload.contactNature2()).isEqualTo("代理");
        assertThat(payload.registrationDeadline()).isEqualTo(LocalDateTime.of(2026, 9, 10, 17, 0));
        assertThat(payload.bidOpeningTime()).isEqualTo(LocalDateTime.of(2026, 9, 15, 9, 30));

        ArgumentCaptor<TenderEventLogRecord> log = ArgumentCaptor.forClass(TenderEventLogRecord.class);
        verify(eventLog).record(log.capture());
        assertThat(log.getValue().status()).isEqualTo(TenderEventStatus.SENT);
        assertThat(log.getValue().tenderId()).isEqualTo(1L);
        assertThat(log.getValue().eventCode()).isEqualTo("BID_TENDER_CHANGE");
        assertThat(log.getValue().eventContent()).isEqualTo("{\"tenderId\":1}");
    }

    @Test
    @DisplayName("不超过18位的非空 MDC traceId 原样复用")
    void shortMdcTrace_isReused() {
        when(payloadMapper.toJson(any())).thenReturn("{\"tenderId\":1}");
        when(publisher.publish(any())).thenReturn(true);
        MDC.put("traceId", "trace-123");
        try {
            buildService(true).publishOnCreate(tender(1L, Tender.SourceType.MANUAL_SINGLE));
        } finally {
            MDC.clear();
        }

        ArgumentCaptor<TenderEventPublishCommand> command = ArgumentCaptor.forClass(TenderEventPublishCommand.class);
        verify(publisher).publish(command.capture());
        assertThat(command.getValue().traceId()).isEqualTo("trace-123");
        assertThat(command.getValue().spanId()).matches("^s[0-9a-z]+$");
        assertThat(command.getValue().parentId()).isEqualTo("0");
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
