// Input: QualificationEntity + User + NotificationAppService mock
// Output: §4.1.3.8 QualificationExpiryNotificationService 跳过规则 + 通知下发单测
// Pos: test/java/.../alerts/service - 应用服务单测
// 维护声明: 跳过规则（RETIRED/OUT_OF_WINDOW/REMINDER_DISABLED/DEDUP_24H/INVALID_VALIDITY）逐条覆盖.
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.businessqualification.domain.service.QualificationExpiryPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualificationExpiryNotificationServiceTest {

    /** 测试中假定的 system actor id，对应于 stub 的 admin 用户。 */
    private static final Long SYSTEM_ACTOR_ID = 1L;

    @Mock private BusinessQualificationJpaRepository qualificationJpaRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationApplicationService notificationApplicationService;

    private QualificationExpiryNotificationService service;

    @BeforeEach
    void setUp() {
        SystemActorResolver resolver = mock(SystemActorResolver.class);
        lenient().when(resolver.resolveCached()).thenReturn(SYSTEM_ACTOR_ID);
        service = new QualificationExpiryNotificationService(
                qualificationJpaRepository,
                userRepository,
                notificationApplicationService,
                new QualificationExpiryPolicy(),
                resolver
        );
    }

    @Test
    @DisplayName("跳过规则 - RETIRED：Jpa 查询已排除，但应用服务二次防御仍识别")
    void shouldSkip_RetiredEntity_ShouldReturnRETIRED() {
        BusinessQualificationEntity q = sample(LocalDate.now().plusDays(10), QualificationStatus.RETIRED);
        LocalDateTime now = LocalDateTime.now();
        QualificationExpiryNotificationService.SkipReason reason =
                service.shouldSkip(q, LocalDate.now(), 30, now);
        assertThat(reason).isEqualTo(QualificationExpiryNotificationService.SkipReason.RETIRED);
    }

    @Test
    @DisplayName("跳过规则 - expiryDate 为 null：INVALID_VALIDITY")
    void shouldSkip_NullExpiryDate_ShouldReturnInvalidValidity() {
        BusinessQualificationEntity q = sample(null, QualificationStatus.IN_STOCK);
        LocalDateTime now = LocalDateTime.now();
        QualificationExpiryNotificationService.SkipReason reason =
                service.shouldSkip(q, LocalDate.now(), 30, now);
        assertThat(reason).isEqualTo(QualificationExpiryNotificationService.SkipReason.INVALID_VALIDITY);
    }

    @Test
    @DisplayName("跳过规则 - reminderPolicy.enabled=false：REMINDER_DISABLED")
    void shouldSkip_DisabledReminder_ShouldReturnReminderDisabled() {
        BusinessQualificationEntity q = sample(LocalDate.now().plusDays(20), QualificationStatus.IN_STOCK);
        q.setReminderEnabled(false);
        LocalDateTime now = LocalDateTime.now();
        QualificationExpiryNotificationService.SkipReason reason =
                service.shouldSkip(q, LocalDate.now(), 30, now);
        assertThat(reason).isEqualTo(QualificationExpiryNotificationService.SkipReason.REMINDER_DISABLED);
    }

    @Test
    @DisplayName("跳过规则 - 续期后剩余天数 > 阈值：OUT_OF_WINDOW")
    void shouldSkip_RenewedBeyondThreshold_ShouldReturnOutOfWindow() {
        BusinessQualificationEntity q = sample(LocalDate.now().plusDays(200), QualificationStatus.IN_STOCK);
        LocalDateTime now = LocalDateTime.now();
        QualificationExpiryNotificationService.SkipReason reason =
                service.shouldSkip(q, LocalDate.now(), 30, now);
        assertThat(reason).isEqualTo(QualificationExpiryNotificationService.SkipReason.OUT_OF_WINDOW);
    }

    @Test
    @DisplayName("跳过规则 - 24h 内已提醒：DEDUP_24H")
    void shouldSkip_RecentlyReminded_ShouldReturnDedup24h() {
        BusinessQualificationEntity q = sample(LocalDate.now().plusDays(10), QualificationStatus.IN_STOCK);
        q.setLastRemindedAt(LocalDateTime.now().minusHours(1));
        LocalDateTime now = LocalDateTime.now();
        QualificationExpiryNotificationService.SkipReason reason =
                service.shouldSkip(q, LocalDate.now(), 30, now);
        assertThat(reason).isEqualTo(QualificationExpiryNotificationService.SkipReason.DEDUP_24H);
    }

    @Test
    @DisplayName("跳过规则 - 全部通过：null（应继续发送）")
    void shouldSkip_AllChecksPass_ShouldReturnNull() {
        BusinessQualificationEntity q = sample(LocalDate.now().plusDays(10), QualificationStatus.IN_STOCK);
        q.setLastRemindedAt(LocalDateTime.now().minusDays(2));
        LocalDateTime now = LocalDateTime.now();
        QualificationExpiryNotificationService.SkipReason reason =
                service.shouldSkip(q, LocalDate.now(), 30, now);
        assertThat(reason).isNull();
    }

    @Test
    @DisplayName("runScan - 命中有效证书：发送 DEADLINE 通知给三个角色 + 更新 lastRemindedAt")
    void runScan_HitValidCert_ShouldSendDeadlineAndUpdateLastRemindedAt() {
        BusinessQualificationEntity q = sample(LocalDate.now().plusDays(15), QualificationStatus.IN_STOCK);
        when(qualificationJpaRepository.findByExpiryDateLessThanEqualAndStatusNot(
                any(LocalDate.class), eq(QualificationStatus.RETIRED)))
                .thenReturn(List.of(q));
        when(userRepository.findEnabledByRoleProfileCodes(any()))
                .thenReturn(List.of(user(101L), user(102L), user(103L)));
        when(notificationApplicationService.createNotification(any(), any()))
                .thenReturn(com.xiyu.bid.notification.core.NotificationDispatchPolicy.DispatchResult.validWithId(1L));

        QualificationExpiryNotificationService.ScanOutcome outcome = service.runScan(30, null);

        assertThat(outcome.scanned()).isEqualTo(1);
        assertThat(outcome.notified()).isEqualTo(1);
        assertThat(outcome.skipped()).isEqualTo(0);

        ArgumentCaptor<CreateNotificationRequest> requestCaptor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationApplicationService).createNotification(requestCaptor.capture(), eq(SYSTEM_ACTOR_ID));
        CreateNotificationRequest req = requestCaptor.getValue();
        assertThat(req.type()).isEqualTo("DEADLINE");
        assertThat(req.sourceEntityType()).isEqualTo("Qualification");
        assertThat(req.sourceEntityId()).isEqualTo(q.getId());
        assertThat(req.title()).contains("【资质到期提醒】").contains("15 天到期");
        assertThat(req.recipientUserIds()).containsExactly(101L, 102L, 103L);
        assertThat(req.body())
                .contains("① 证书名称：")
                .contains("② 证书号：")
                .contains("③ 等级：")
                .contains("⑨ 跳转详情：");

        verify(qualificationJpaRepository).save(q);
        assertThat(q.getLastRemindedAt()).isNotNull();
    }

    @Test
    @DisplayName("runScan - 24h 内同证书重复扫描：被去重（不重复发）")
    void runScan_DuplicateWithin24h_ShouldSkipByDedup() {
        BusinessQualificationEntity q = sample(LocalDate.now().plusDays(15), QualificationStatus.IN_STOCK);
        q.setLastRemindedAt(LocalDateTime.now().minusHours(3));
        when(qualificationJpaRepository.findByExpiryDateLessThanEqualAndStatusNot(
                any(LocalDate.class), eq(QualificationStatus.RETIRED)))
                .thenReturn(List.of(q));
        when(userRepository.findEnabledByRoleProfileCodes(any()))
                .thenReturn(List.of(user(101L)));

        QualificationExpiryNotificationService.ScanOutcome outcome = service.runScan(30, null);

        assertThat(outcome.scanned()).isEqualTo(1);
        assertThat(outcome.notified()).isEqualTo(0);
        assertThat(outcome.skipped()).isEqualTo(1);
        verify(notificationApplicationService, never()).createNotification(any(), any());
        // lastRemindedAt 不会被覆盖（没有再次写库）
        verify(qualificationJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("runScan - 无任何接收人：中止派发")
    void runScan_NoRecipients_ShouldAbort() {
        BusinessQualificationEntity q = sample(LocalDate.now().plusDays(15), QualificationStatus.IN_STOCK);
        when(qualificationJpaRepository.findByExpiryDateLessThanEqualAndStatusNot(
                any(LocalDate.class), eq(QualificationStatus.RETIRED)))
                .thenReturn(List.of(q));
        when(userRepository.findEnabledByRoleProfileCodes(any())).thenReturn(List.of());

        QualificationExpiryNotificationService.ScanOutcome outcome = service.runScan(30, null);

        assertThat(outcome.scanned()).isEqualTo(1);
        assertThat(outcome.notified()).isEqualTo(0);
        assertThat(outcome.skipped()).isEqualTo(1);
        verify(notificationApplicationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("runScan - 非法 alertDays：直接返回 empty")
    void runScan_InvalidAlertDays_ShouldReturnEmpty() {
        QualificationExpiryNotificationService.ScanOutcome outcome = service.runScan(0, null);
        assertThat(outcome.scanned()).isZero();
        assertThat(outcome.notified()).isZero();
        verify(qualificationJpaRepository, never()).findByExpiryDateLessThanEqualAndStatusNot(any(), any());
    }

    private BusinessQualificationEntity sample(LocalDate expiryDate, QualificationStatus status) {
        BusinessQualificationEntity q = new BusinessQualificationEntity();
        q.setId(1L);
        q.setName("测试证书 ABC");
        q.setSubjectType(QualificationSubjectType.COMPANY);
        q.setSubjectName("测试公司");
        q.setCategory(QualificationCategory.OTHER);
        q.setCertificateNo("CN-2024-001");
        q.setIssuer("国家计量局");
        q.setAgency("中兴代理");
        q.setAgencyContact("13800000000");
        q.setLevel("甲级");
        q.setIssueDate(LocalDate.of(2024, 1, 1));
        q.setExpiryDate(expiryDate);
        q.setStatus(status);
        q.setReminderEnabled(true);
        q.setReminderDays(30);
        return q;
    }

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setEnabled(true);
        return u;
    }
}
