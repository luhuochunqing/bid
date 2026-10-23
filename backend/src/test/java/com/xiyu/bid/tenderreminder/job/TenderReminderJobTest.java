package com.xiyu.bid.tenderreminder.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tenderreminder.entity.ReminderType;
import com.xiyu.bid.tenderreminder.entity.TenderReminderSetting;
import com.xiyu.bid.tenderreminder.repository.TenderReminderLogRepository;
import com.xiyu.bid.tenderreminder.repository.TenderReminderSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenderReminderJob 单元测试。
 *
 * <p>覆盖企微文案修复：提醒标题与正文拆分承载（title / body），
 * 此前 title + "\n" + content 合并塞入 title，企微 title 超 128 字符截断会丢正文尾部。
 */
@ExtendWith(MockitoExtension.class)
class TenderReminderJobTest {

    @Mock
    private TenderReminderSettingRepository settingRepository;

    @Mock
    private TenderReminderLogRepository logRepository;

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private WeComPushService weComPushService;

    @Mock
    private TenderAutoReminderScanner autoReminderScanner;

    @Test
    void processReminders_shouldSplitTitleAndBody() {
        LocalDateTime now = LocalDateTime.now();

        TenderReminderSetting setting = new TenderReminderSetting();
        setting.setId(1L);
        setting.setTenderId(10L);
        setting.setReminderType(ReminderType.REGISTRATION_DEADLINE);
        setting.setRemindBeforeHours(72);
        setting.setReminderTargets("[{\"userId\":7,\"userName\":\"张三\",\"wecomUserId\":\"w7\"}]");
        when(settingRepository.findByEnabledTrue()).thenReturn(List.of(setting));

        Tender tender = Tender.builder()
                .id(10L)
                .title("某医院设备采购项目")
                .registrationDeadline(now.plusHours(24))
                .build();
        when(tenderRepository.findById(10L)).thenReturn(Optional.of(tender));
        when(autoReminderScanner.scan(any())).thenReturn(TenderAutoReminderScanner.ScanResult.empty());

        TenderReminderJob job = new TenderReminderJob(
                settingRepository, logRepository, tenderRepository,
                weComPushService, new ObjectMapper(), autoReminderScanner);
        job.processReminders();

        ArgumentCaptor<NotificationCreatedEvent> captor = ArgumentCaptor.forClass(NotificationCreatedEvent.class);
        verify(weComPushService).pushForRecipient(captor.capture(), eq(7L));

        NotificationCreatedEvent event = captor.getValue();
        assertThat(event.title())
                .isEqualTo("【报名截止提醒】某医院设备采购项目")
                .doesNotContain("\n");
        assertThat(event.body())
                .contains("报名截止提醒：标讯「某医院设备采购项目」即将报名截止")
                .contains("提前提醒：72小时");
        assertThat(event.type()).isEqualTo("TENDER_REMINDER");
        assertThat(event.sourceEntityType()).isEqualTo("TENDER");
        assertThat(event.sourceEntityId()).isEqualTo(10L);
    }

    @Test
    void processReminders_keepsManualSendWhenAutoScanFails() {
        LocalDateTime now = LocalDateTime.now();
        TenderReminderSetting setting = new TenderReminderSetting();
        setting.setId(1L);
        setting.setTenderId(10L);
        setting.setReminderType(ReminderType.REGISTRATION_DEADLINE);
        setting.setRemindBeforeHours(72);
        setting.setReminderTargets("[{\"userId\":7,\"userName\":\"张三\",\"wecomUserId\":\"w7\"}]");
        when(settingRepository.findByEnabledTrue()).thenReturn(List.of(setting));
        when(tenderRepository.findById(10L)).thenReturn(Optional.of(Tender.builder()
                .id(10L)
                .title("某医院设备采购项目")
                .registrationDeadline(now.plusHours(24))
                .build()));
        doThrow(new IllegalStateException("auto scan down")).when(autoReminderScanner).scan(any());

        new TenderReminderJob(
                settingRepository, logRepository, tenderRepository,
                weComPushService, new ObjectMapper(), autoReminderScanner)
                .processReminders();

        verify(weComPushService).pushForRecipient(any(), eq(7L));
        verify(logRepository).save(any());
    }
}
