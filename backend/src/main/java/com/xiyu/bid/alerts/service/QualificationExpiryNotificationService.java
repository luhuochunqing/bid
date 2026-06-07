// Input: AlertConfig (alertDays/enabled), expiring qualifications, user directory
// Output: 蓝图 §4.1.3.8 资质到期提醒（站内信 + 企微推送 + 24h 去重 + 续期/下架停止）
// Pos: Service/业务层 - alerts 模块下的提醒编排（贴近 alerts README §"跨模块提醒统一由 alertdispatch 包编排" 的边界精神）
// 维护声明:
//   - 纯规则（24h 去重 / 续期窗口 / 提醒策略是否启用）下沉到 QualificationExpiryPolicy；
//   - 模板文案沉淀到 QualificationExpiryAlertMessage；
//   - 本类只做编排：找证书、找接收人、调通知、更新最后提醒时间；
//   - 不修改入参、不抛业务分支异常（用 ScanOutcome 返回值表达）。
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.businessqualification.application.view.QualificationExpiryAlertMessage;
import com.xiyu.bid.businessqualification.domain.service.QualificationExpiryPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * §4.1.3.8 资质到期提醒编排：扫描 → 过滤 → 模板 → 站内信 + 企微 → 更新最后提醒时间。
 * <p>
 * 蓝图规则：
 * <ul>
 *   <li>接收人：行政人员（admin_staff）、投标管理员（bid_admin）、投标组长（bid_lead）</li>
 *   <li>渠道：站内信（Notification）+ 企微（NotificationCreatedWeComListener 自动转发）</li>
 *   <li>频次：每张证书每日至多 1 次（lastRemindedAt + 24h 去重）</li>
 *   <li>跳过：下架（status=RETIRED）、续期（remainingDays > alertDays）、提醒策略被关闭</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QualificationExpiryNotificationService {

    /** 蓝图 §4.1.3.8 接收人角色：行政人员、投标管理员、投标组长。 */
    static final List<String> RECIPIENT_ROLE_CODES = List.of(
            "admin_staff", "bid_admin", "bid_lead"
    );

    private final BusinessQualificationJpaRepository qualificationJpaRepository;
    private final UserRepository userRepository;
    private final NotificationApplicationService notificationApplicationService;
    private final QualificationExpiryPolicy expiryPolicy;
    private final SystemActorResolver systemActorResolver;

    /** 时钟：方便测试；生产用系统默认时钟。 */
    private Clock clock = Clock.systemDefaultZone();

    /** 测试或运维场景下注入固定时钟。 */
    public void setClock(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    /**
     * 主入口：扫描即将到期的资质，对接收人发送站内信 + 企微推送。
     *
     * @param alertDays   提前提醒天数（来自 AlertConfig 或调用方）
     * @param detailUrlBase 跳转链接基础 URL（null 时使用默认 /knowledge/qualification?id={id}）
     * @return 扫描结果（已发送数、跳过数、命中数）
     */
    @Transactional
    public ScanOutcome runScan(int alertDays, String detailUrlBase) {
        final LocalDateTime now = LocalDateTime.now(clock);
        final LocalDate today = LocalDate.now(clock);

        if (alertDays < 1 || alertDays > 365) {
            log.warn("Invalid alertDays={} (must be 1..365), aborting scan", alertDays);
            return ScanOutcome.empty();
        }

        // 1. 找证书：到期日 <= today + alertDays 且未下架
        List<BusinessQualificationEntity> candidates = qualificationJpaRepository
                .findByExpiryDateLessThanEqualAndStatusNot(today.plusDays(alertDays), QualificationStatus.RETIRED);
        log.info("[§4.1.3.8] scanning {} candidate qualifications (alertDays={})", candidates.size(), alertDays);

        // 2. 找接收人
        List<Long> recipientIds = resolveRecipientUserIds();
        if (recipientIds.isEmpty()) {
            log.warn("[§4.1.3.8] no enabled recipient users (admin_staff/bid_admin/bid_lead), aborting dispatch");
            return new ScanOutcome(candidates.size(), 0, candidates.size(), List.of());
        }

        // 3. 解析 system actor（定时任务无登录态，必须显式提供 created_by）
        Long systemActor = systemActorResolver.resolveCached();
        if (systemActor == null) {
            log.warn("[§4.1.3.8] system actor unresolved, aborting dispatch to avoid created_by=null");
            return new ScanOutcome(candidates.size(), 0, candidates.size(), List.of());
        }

        int notified = 0;
        int skipped = 0;
        List<NotifiedCert> notifiedCerts = new ArrayList<>();

        for (BusinessQualificationEntity q : candidates) {
            SkipReason reason = shouldSkip(q, today, alertDays, now);
            if (reason != null) {
                log.debug("[§4.1.3.8] skip qualification id={} reason={}", q.getId(), reason);
                skipped++;
                continue;
            }

            long remaining = computeRemainingDays(q.getExpiryDate(), today);
            String link = buildLink(detailUrlBase, q);
            QualificationExpiryAlertMessage msg = QualificationExpiryAlertMessage.from(toDomainLike(q), remaining, q.getLevel(), link);

            try {
                notificationApplicationService.createNotification(
                        new CreateNotificationRequest(
                                NotificationType.DEADLINE.name(),
                                "Qualification",
                                q.getId(),
                                msg.title(),
                                msg.body(),
                                buildPayload(q, remaining, alertDays, link),
                                recipientIds
                        ),
                        systemActor
                );
                q.setLastRemindedAt(now);
                qualificationJpaRepository.save(q);
                notified++;
                notifiedCerts.add(new NotifiedCert(q.getId(), q.getName(), remaining));
            } catch (RuntimeException ex) {
                log.error("[§4.1.3.8] failed to dispatch notification for qualification id={} name={}: {}",
                        q.getId(), q.getName(), ex.getMessage(), ex);
                skipped++;
            }
        }

        log.info("[§4.1.3.8] scan done. scanned={} notified={} skipped={} recipients={}",
                candidates.size(), notified, skipped, recipientIds.size());
        return new ScanOutcome(candidates.size(), notified, skipped, notifiedCerts);
    }

    /** 跳过原因枚举（仅做日志和返回值，不抛异常）。 */
    enum SkipReason {
        /** 证书已下架（status=RETIRED）。 */
        RETIRED,
        /** 续期后剩余天数 > 阈值，不在提醒窗口。 */
        OUT_OF_WINDOW,
        /** 提醒策略被关闭（reminderPolicy.enabled=false）。 */
        REMINDER_DISABLED,
        /** 24 小时内已提醒过。 */
        DEDUP_24H,
        /** 证书有效期字段缺失。 */
        INVALID_VALIDITY
    }

    /**
     * 判定是否应跳过：仅做单证书判定，不读数据库。
     * <p>
     * 注意 RETIRED 已在 JPA 查询层排除；此处保留防御性检查以兼容未来调用方。
     */
    SkipReason shouldSkip(BusinessQualificationEntity q, LocalDate today, int alertDays, LocalDateTime now) {
        if (q.getStatus() == QualificationStatus.RETIRED) {
            return SkipReason.RETIRED;
        }
        if (q.getExpiryDate() == null) {
            return SkipReason.INVALID_VALIDITY;
        }
        ReminderPolicy policy = new ReminderPolicy(q.isReminderEnabled(), q.getReminderDays(), q.getLastRemindedAt());
        if (!expiryPolicy.isReminderActive(policy)) {
            return SkipReason.REMINDER_DISABLED;
        }
        long remaining = computeRemainingDays(q.getExpiryDate(), today);
        if (remaining > alertDays || remaining < 0) {
            return SkipReason.OUT_OF_WINDOW;
        }
        if (!expiryPolicy.shouldRemindToday(policy.getLastRemindedAt(), now)) {
            return SkipReason.DEDUP_24H;
        }
        return null;
    }

    private List<Long> resolveRecipientUserIds() {
        try {
            return userRepository.findEnabledByRoleProfileCodes(RECIPIENT_ROLE_CODES).stream()
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (RuntimeException ex) {
            log.error("[§4.1.3.8] failed to resolve recipients by role codes {}: {}",
                    RECIPIENT_ROLE_CODES, ex.getMessage(), ex);
            return List.of();
        }
    }

    private static String buildLink(String detailUrlBase, BusinessQualificationEntity q) {
        if (detailUrlBase == null || detailUrlBase.isBlank()) {
            return QualificationExpiryAlertMessage.buildDefaultLink(q.getId());
        }
        String trimmed = detailUrlBase.endsWith("/")
                ? detailUrlBase.substring(0, detailUrlBase.length() - 1)
                : detailUrlBase;
        return trimmed + "/knowledge/qualification?id=" + q.getId();
    }

    private static long computeRemainingDays(LocalDate expiryDate, LocalDate today) {
        if (expiryDate == null) {
            return Long.MAX_VALUE;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(today, expiryDate);
    }

    /**
     * 把 entity 临时映射成 domain record 的同形视图（仅用于消息模板，不持久化）。
     * <p>
     * 这样 {@link QualificationExpiryAlertMessage#from} 的纯核心签名无需变化。
     */
    private static com.xiyu.bid.businessqualification.domain.model.BusinessQualification toDomainLike(
            BusinessQualificationEntity e) {
        com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject subject =
                com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject.of(
                        e.getSubjectType(), e.getSubjectName());
        com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod validity =
                new com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod(
                        e.getIssueDate(), e.getExpiryDate());
        com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy policy =
                new com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy(
                        e.isReminderEnabled(), e.getReminderDays(), e.getLastRemindedAt());
        return com.xiyu.bid.businessqualification.domain.model.BusinessQualification.create(
                e.getId(), e.getName(), subject, e.getCategory(),
                e.getCertificateNo(), e.getIssuer(), e.getAgency(), e.getAgencyContact(),
                e.getCertScope(), e.getCertReviewNote(), e.getHolderName(),
                validity, policy,
                e.getCurrentBorrowStatus() == null
                        ? com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus.AVAILABLE
                        : e.getCurrentBorrowStatus(),
                e.getCurrentBorrower(), e.getCurrentDepartment(),
                // 当前提醒流程不关心借阅项目归属，强制 null 避免污染模板。
                null,
                e.getBorrowPurpose(), e.getExpectedReturnDate(),
                e.getFileUrl(), java.util.List.of()
        );
    }

    private static Map<String, Object> buildPayload(
            BusinessQualificationEntity q, long remainingDays, int alertDays, String link) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qualificationId", q.getId());
        payload.put("qualificationName", q.getName());
        payload.put("certificateNo", q.getCertificateNo());
        payload.put("remainingDays", remainingDays);
        payload.put("alertDays", alertDays);
        payload.put("expiryDate", q.getExpiryDate() == null ? null : q.getExpiryDate().toString());
        payload.put("detailUrl", link);
        return payload;
    }

    /** 扫描结果。 */
    public record ScanOutcome(int scanned, int notified, int skipped, List<NotifiedCert> notifiedCerts) {
        public static ScanOutcome empty() {
            return new ScanOutcome(0, 0, 0, List.of());
        }
    }

    /** 已发送提醒的证书摘要。 */
    public record NotifiedCert(Long qualificationId, String name, long remainingDays) {
    }
}
