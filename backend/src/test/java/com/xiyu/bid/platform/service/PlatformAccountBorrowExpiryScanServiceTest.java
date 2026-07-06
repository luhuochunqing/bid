// Input: AccountBorrowApplicationRepository, PlatformAccountRepository, UserRepository, NotificationApplicationService, NotificationRepository mocks
// Output: PlatformAccountBorrowExpiryScanService unit tests — expiry / overdue / pending reminder rules
// Pos: Test/纯核心 + 编排验证
package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.DispatchResult;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.repository.NotificationRepository;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.platform.entity.AccountBorrowApplication;
import com.xiyu.bid.platform.entity.AccountBorrowApplication.BorrowStatus;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.entity.PlatformAccount.AccountStatus;
import com.xiyu.bid.platform.repository.AccountBorrowApplicationRepository;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformAccountBorrowExpiryScanServiceTest {

    @Mock
    private AccountBorrowApplicationRepository applicationRepository;
    @Mock
    private PlatformAccountRepository accountRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationApplicationService notificationService;
    @Mock
    private NotificationRepository notificationRepository;

    private PlatformAccountBorrowExpiryScanService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 9, 0, 0);
    private static final Long APPLICANT_ID = 10L;
    private static final Long CUSTODIAN_ID = 20L;
    private static final Long BID_ADMIN_ID = 30L;
    private static final Long ACCOUNT_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new PlatformAccountBorrowExpiryScanService(
                applicationRepository, accountRepository, userRepository,
                notificationService, notificationRepository);

        User bidAdmin = User.builder().id(BID_ADMIN_ID).build();
        lenient().when(userRepository.findEnabledByRoleProfileCodes(List.of("/bidAdmin")))
                .thenReturn(List.of(bidAdmin));
        lenient().when(notificationService.createNotification(any(), any()))
                .thenReturn(DispatchResult.validWithId(999L));
        lenient().when(applicationRepository.findByStatus(any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("待审批申请应提醒账户保管员")
    void scan_pendingApplication_sendsReminderToCustodian() {
        AccountBorrowApplication app = pendingApp();
        when(applicationRepository.findByStatus(BorrowStatus.PENDING_APPROVAL))
                .thenReturn(List.of(app));
        when(notificationRepository.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(
                eq(PlatformAccountBorrowExpiryScanService.SOURCE_PENDING), eq(app.getId()), any()))
                .thenReturn(false);

        int sent = service.scan(NOW);

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), eq(0L));
        CreateNotificationRequest req = captor.getValue();
        assertThat(req.recipientUserIds()).containsExactly(CUSTODIAN_ID);
        assertThat(req.title()).contains("账号借用申请待审批");
        assertThat(req.sourceEntityType()).isEqualTo(PlatformAccountBorrowExpiryScanService.SOURCE_PENDING);
    }

    @Test
    @DisplayName("待审批申请 24h 内已提醒则跳过")
    void scan_pendingApplicationAlreadyRemindedToday_skips() {
        AccountBorrowApplication app = pendingApp();
        when(applicationRepository.findByStatus(BorrowStatus.PENDING_APPROVAL))
                .thenReturn(List.of(app));
        when(notificationRepository.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(
                eq(PlatformAccountBorrowExpiryScanService.SOURCE_PENDING), eq(app.getId()), any()))
                .thenReturn(true);

        int sent = service.scan(NOW);

        assertThat(sent).isEqualTo(0);
        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("已借出且预计归还还有 1 天应提醒借用人和保管员")
    void scan_borrowedWithOneDayLeft_sendsExpiringReminder() {
        AccountBorrowApplication app = borrowedApp(NOW.plusDays(1));
        when(applicationRepository.findByStatus(BorrowStatus.BORROWED))
                .thenReturn(List.of(app));
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(activeAccount()));
        when(notificationRepository.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(
                eq(PlatformAccountBorrowExpiryScanService.SOURCE_EXPIRING), eq(app.getId()), any()))
                .thenReturn(false);

        int sent = service.scan(NOW);

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), eq(0L));
        CreateNotificationRequest req = captor.getValue();
        assertThat(req.recipientUserIds()).containsExactlyInAnyOrder(APPLICANT_ID, CUSTODIAN_ID);
        assertThat(req.title()).contains("账号归还提醒");
        assertThat(req.sourceEntityType()).isEqualTo(PlatformAccountBorrowExpiryScanService.SOURCE_EXPIRING);
    }

    @Test
    @DisplayName("已借出且预计归还剩余 3 天不提醒")
    void scan_borrowedWithThreeDaysLeft_noReminder() {
        AccountBorrowApplication app = borrowedApp(NOW.plusDays(3));
        when(applicationRepository.findByStatus(BorrowStatus.BORROWED))
                .thenReturn(List.of(app));

        int sent = service.scan(NOW);

        assertThat(sent).isEqualTo(0);
        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("已借出且已逾期 1 天应提醒借用人、保管员和投标管理员")
    void scan_borrowedOverdueOneDay_sendsOverdueReminder() {
        AccountBorrowApplication app = borrowedApp(NOW.minusDays(1));
        when(applicationRepository.findByStatus(BorrowStatus.BORROWED))
                .thenReturn(List.of(app));
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(activeAccount()));
        when(notificationRepository.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(
                eq(PlatformAccountBorrowExpiryScanService.SOURCE_OVERDUE), eq(app.getId()), any()))
                .thenReturn(false);

        int sent = service.scan(NOW);

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<CreateNotificationRequest> captor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).createNotification(captor.capture(), eq(0L));
        CreateNotificationRequest req = captor.getValue();
        assertThat(req.recipientUserIds()).containsExactlyInAnyOrder(APPLICANT_ID, CUSTODIAN_ID, BID_ADMIN_ID);
        assertThat(req.title()).contains("账号借用已逾期");
        assertThat(req.sourceEntityType()).isEqualTo(PlatformAccountBorrowExpiryScanService.SOURCE_OVERDUE);
    }

    @Test
    @DisplayName("已逾期申请 24h 内已提醒则跳过")
    void scan_overdueAlreadyRemindedToday_skips() {
        AccountBorrowApplication app = borrowedApp(NOW.minusDays(1));
        when(applicationRepository.findByStatus(BorrowStatus.BORROWED))
                .thenReturn(List.of(app));
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(activeAccount()));
        when(notificationRepository.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(
                eq(PlatformAccountBorrowExpiryScanService.SOURCE_OVERDUE), eq(app.getId()), any()))
                .thenReturn(true);

        int sent = service.scan(NOW);

        assertThat(sent).isEqualTo(0);
        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("账号已禁用（下架）时跳过借用到期提醒")
    void scan_accountDisabled_skipsBorrowedReminder() {
        AccountBorrowApplication app = borrowedApp(NOW.plusDays(1));
        when(applicationRepository.findByStatus(BorrowStatus.BORROWED))
                .thenReturn(List.of(app));
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(disabledAccount()));

        int sent = service.scan(NOW);

        assertThat(sent).isEqualTo(0);
        verify(notificationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("通知发送失败时不阻断后续扫描")
    void scan_notificationThrows_continuesAndLogsError() {
        AccountBorrowApplication app = pendingApp();
        when(applicationRepository.findByStatus(BorrowStatus.PENDING_APPROVAL))
                .thenReturn(List.of(app));
        when(notificationRepository.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(
                eq(PlatformAccountBorrowExpiryScanService.SOURCE_PENDING), eq(app.getId()), any()))
                .thenReturn(false);
        when(notificationService.createNotification(any(), any()))
                .thenThrow(new RuntimeException("WeCom timeout"));

        int sent = service.scan(NOW);

        assertThat(sent).isEqualTo(0);
    }

    @Test
    @DisplayName("纯核心判断：PENDING_APPROVAL 返回待审批类型")
    void determineReminderType_pending_returnsPending() {
        AccountBorrowApplication app = pendingApp();

        PlatformAccountBorrowExpiryScanService.ReminderType type =
                PlatformAccountBorrowExpiryScanService.determineReminderType(app, NOW);

        assertThat(type).isEqualTo(PlatformAccountBorrowExpiryScanService.ReminderType.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("纯核心判断：BORROWED 且 1 天后到期返回即将到期")
    void determineReminderType_borrowedOneDayLeft_returnsExpiring() {
        AccountBorrowApplication app = borrowedApp(NOW.plusDays(1));

        PlatformAccountBorrowExpiryScanService.ReminderType type =
                PlatformAccountBorrowExpiryScanService.determineReminderType(app, NOW);

        assertThat(type).isEqualTo(PlatformAccountBorrowExpiryScanService.ReminderType.EXPIRING_SOON);
    }

    @Test
    @DisplayName("纯核心判断：BORROWED 且已逾期返回已逾期")
    void determineReminderType_borrowedOverdue_returnsOverdue() {
        AccountBorrowApplication app = borrowedApp(NOW.minusDays(1));

        PlatformAccountBorrowExpiryScanService.ReminderType type =
                PlatformAccountBorrowExpiryScanService.determineReminderType(app, NOW);

        assertThat(type).isEqualTo(PlatformAccountBorrowExpiryScanService.ReminderType.OVERDUE);
    }

    @Test
    @DisplayName("纯核心判断：RETURNED 返回 null")
    void determineReminderType_returned_returnsNull() {
        AccountBorrowApplication app = AccountBorrowApplication.builder()
                .id(100L).accountId(ACCOUNT_ID).applicantId(APPLICANT_ID).custodianId(CUSTODIAN_ID)
                .status(BorrowStatus.RETURNED).build();

        PlatformAccountBorrowExpiryScanService.ReminderType type =
                PlatformAccountBorrowExpiryScanService.determineReminderType(app, NOW);

        assertThat(type).isNull();
    }

    private AccountBorrowApplication pendingApp() {
        return AccountBorrowApplication.builder()
                .id(100L).accountId(ACCOUNT_ID).applicantId(APPLICANT_ID).custodianId(CUSTODIAN_ID)
                .status(BorrowStatus.PENDING_APPROVAL)
                .purpose("投标使用")
                .expectedReturnAt(NOW.plusDays(5))
                .build();
    }

    private AccountBorrowApplication borrowedApp(LocalDateTime expectedReturnAt) {
        return AccountBorrowApplication.builder()
                .id(101L).accountId(ACCOUNT_ID).applicantId(APPLICANT_ID).custodianId(CUSTODIAN_ID)
                .status(BorrowStatus.BORROWED)
                .purpose("投标使用")
                .expectedReturnAt(expectedReturnAt)
                .approvedAt(NOW.minusDays(5))
                .build();
    }

    private PlatformAccount activeAccount() {
        return PlatformAccount.builder()
                .id(ACCOUNT_ID).accountName("政采云投标平台").status(AccountStatus.IN_USE)
                .contactPerson(CUSTODIAN_ID)
                .build();
    }

    private PlatformAccount disabledAccount() {
        return PlatformAccount.builder()
                .id(ACCOUNT_ID).accountName("政采云投标平台").status(AccountStatus.DISABLED)
                .build();
    }
}
