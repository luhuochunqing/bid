package com.xiyu.bid.tenderreminder.job;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.service.NotificationDeliveryResult;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tenderreminder.entity.ReminderType;
import com.xiyu.bid.tenderreminder.entity.TenderReminderLog;
import com.xiyu.bid.tenderreminder.repository.TenderReminderLogRepository;
import com.xiyu.bid.tenderreminder.repository.TenderReminderSettingRepository;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderAutoReminderScannerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 11, 0);

    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private TenderReminderSettingRepository settingRepository;
    @Mock
    private TenderReminderLogRepository logRepository;
    @Mock
    private NotificationRecipientResolver recipientResolver;
    @Mock
    private WeComPushService weComPushService;

    private TenderAutoReminderScanner scanner;
    private ListAppender<ILoggingEvent> diagnostics;

    @AfterEach
    void detachDiagnostics() {
        ((Logger) LoggerFactory.getLogger(TenderAutoReminderScanner.class)).detachAppender(diagnostics);
        diagnostics.stop();
    }

    @BeforeEach
    void setUp() {
        diagnostics = new ListAppender<>();
        diagnostics.start();
        ((Logger) LoggerFactory.getLogger(TenderAutoReminderScanner.class)).addAppender(diagnostics);
        scanner = new TenderAutoReminderScanner(
                tenderRepository, settingRepository, logRepository, recipientResolver, weComPushService);
    }

    @Test
    void scan_sendsRegistrationAndOpeningRemindersWhenNoSettingExists() {
        Tender tender = tender(NOW.plusHours(24), NOW.plusHours(30));
        when(recipientResolver.getAdminUserIds()).thenReturn(List.of(7L, 8L));
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of(tender));
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of(tender));
        when(settingRepository.existsByTenderIdAndReminderType(eq(10L), any())).thenReturn(false);
        when(logRepository.findByTenderIdOrderBySentAtDesc(10L)).thenReturn(List.of());
        when(weComPushService.pushForRecipient(any(), any())).thenReturn(NotificationDeliveryResult.success(0, "ok"));

        TenderAutoReminderScanner.ScanResult result = scanner.scan(NOW);

        ArgumentCaptor<NotificationCreatedEvent> events = ArgumentCaptor.forClass(NotificationCreatedEvent.class);
        verify(weComPushService, times(2)).pushForRecipient(events.capture(), eq(7L));
        verify(weComPushService, times(2)).pushForRecipient(any(), eq(8L));
        assertThat(events.getAllValues())
                .extracting(NotificationCreatedEvent::title)
                .contains("【报名截止提醒】某医院设备采购项目", "【开标提醒】某医院设备采购项目");
        ArgumentCaptor<TenderReminderLog> logs = ArgumentCaptor.forClass(TenderReminderLog.class);
        verify(logRepository, times(4)).save(logs.capture());
        assertThat(logs.getAllValues()).allSatisfy(log -> {
            assertThat(log.getReminderSettingId()).isNull();
            assertThat(log.getStatus()).isEqualTo("SENT");
            assertThat(log.getTenderId()).isEqualTo(10L);
        });
        assertThat(result.sent()).isEqualTo(4);
    }

    @Test
    void scan_skipsTenderOutsideWindowEvenIfQueryReturnsIt() {
        Tender tender = tender(NOW.plusHours(80), null);
        when(recipientResolver.getAdminUserIds()).thenReturn(List.of(7L));
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of(tender));
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of());

        scanner.scan(NOW);

        verify(weComPushService, never()).pushForRecipient(any(), any());
    }

    @Test
    void scan_skipsWhenSettingAlreadyExists() {
        Tender tender = tender(NOW.plusHours(24), null);
        when(recipientResolver.getAdminUserIds()).thenReturn(List.of(7L));
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of(tender));
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of());
        when(settingRepository.existsByTenderIdAndReminderType(10L, ReminderType.REGISTRATION_DEADLINE)).thenReturn(true);

        scanner.scan(NOW);

        verify(weComPushService, never()).pushForRecipient(any(), any());
    }

    @Test
    void scan_skipsRecipientNotifiedWithin24Hours() {
        Tender tender = tender(NOW.plusHours(24), null);
        when(recipientResolver.getAdminUserIds()).thenReturn(List.of(7L, 8L));
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of(tender));
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of());
        when(settingRepository.existsByTenderIdAndReminderType(10L, ReminderType.REGISTRATION_DEADLINE)).thenReturn(false);
        when(logRepository.findByTenderIdOrderBySentAtDesc(10L)).thenReturn(List.of(
                TenderReminderLog.builder()
                        .tenderId(10L)
                        .reminderType(ReminderType.REGISTRATION_DEADLINE)
                        .recipientUserId(7L)
                        .status("SENT")
                        .sentAt(NOW.minusHours(2))
                        .build()));
        when(weComPushService.pushForRecipient(any(), eq(8L))).thenReturn(NotificationDeliveryResult.success(0, "ok"));

        TenderAutoReminderScanner.ScanResult result = scanner.scan(NOW);

        verify(weComPushService, never()).pushForRecipient(any(), eq(7L));
        verify(weComPushService).pushForRecipient(any(), eq(8L));
        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void scan_doesNotRecordSentWhenPushIsSkipped() {
        Tender tender = tender(NOW.plusHours(10), null);
        when(recipientResolver.getAdminUserIds()).thenReturn(List.of(7L));
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of(tender));
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of());
        when(settingRepository.existsByTenderIdAndReminderType(10L, ReminderType.REGISTRATION_DEADLINE)).thenReturn(false);
        when(logRepository.findByTenderIdOrderBySentAtDesc(10L)).thenReturn(List.of());
        when(weComPushService.pushForRecipient(any(), eq(7L)))
                .thenReturn(NotificationDeliveryResult.skip("recipient has no employee number"));

        TenderAutoReminderScanner.ScanResult result = scanner.scan(NOW);

        verify(logRepository, never()).save(any());
        assertThat(result.sent()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(diagnostics.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains(
                        "tenderId=10", "type=REGISTRATION_DEADLINE", "userId=7",
                        "recipient has no employee number"));
    }

    @Test
    void scan_doesNotRecordSentWhenPushFails() {
        Tender tender = tender(NOW.plusHours(10), null);
        when(recipientResolver.getAdminUserIds()).thenReturn(List.of(7L));
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of(tender));
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of());
        when(settingRepository.existsByTenderIdAndReminderType(10L, ReminderType.REGISTRATION_DEADLINE)).thenReturn(false);
        when(logRepository.findByTenderIdOrderBySentAtDesc(10L)).thenReturn(List.of());
        when(weComPushService.pushForRecipient(any(), eq(7L)))
                .thenReturn(NotificationDeliveryResult.failure(1, "wecom down"));

        TenderAutoReminderScanner.ScanResult result = scanner.scan(NOW);

        verify(logRepository, never()).save(any());
        assertThat(result.sent()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(diagnostics.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains(
                        "tenderId=10", "type=REGISTRATION_DEADLINE", "userId=7",
                        "code=1", "wecom down"));
    }

    @Test
    void scan_recordsDiagnosticWhenDeliveryResultIsNull() {
        Tender tender = tender(NOW.plusHours(10), null);
        when(recipientResolver.getAdminUserIds()).thenReturn(List.of(7L));
        when(tenderRepository.findTendersByRegistrationDeadlineBetween(any(), any())).thenReturn(List.of(tender));
        when(tenderRepository.findTendersByBidOpeningTimeBetween(any(), any())).thenReturn(List.of());
        when(settingRepository.existsByTenderIdAndReminderType(10L, ReminderType.REGISTRATION_DEADLINE)).thenReturn(false);
        when(logRepository.findByTenderIdOrderBySentAtDesc(10L)).thenReturn(List.of());
        when(weComPushService.pushForRecipient(any(), eq(7L)))
                .thenReturn(null);

        TenderAutoReminderScanner.ScanResult result = scanner.scan(NOW);

        verify(logRepository, never()).save(any());
        assertThat(result.sent()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(diagnostics.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains(
                        "tenderId=10", "type=REGISTRATION_DEADLINE", "userId=7",
                        "code=null", "empty delivery result"));
    }

    @Test
    void scan_returnsEmptyWhenNoRecipients() {
        when(recipientResolver.getAdminUserIds()).thenReturn(List.of());

        TenderAutoReminderScanner.ScanResult result = scanner.scan(NOW);

        verify(tenderRepository, never()).findTendersByRegistrationDeadlineBetween(any(), any());
        verify(weComPushService, never()).pushForRecipient(any(), any());
        assertThat(result.processed()).isZero();
        assertThat(result.sent()).isZero();
    }

    private static Tender tender(LocalDateTime registrationDeadline, LocalDateTime bidOpeningTime) {
        return Tender.builder()
                .id(10L)
                .title("某医院设备采购项目")
                .registrationDeadline(registrationDeadline)
                .bidOpeningTime(bidOpeningTime)
                .build();
    }
}
