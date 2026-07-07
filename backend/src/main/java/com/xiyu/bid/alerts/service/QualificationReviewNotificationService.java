// Input: BusinessQualificationEntity (certReviewNote) + user directory
// Output: CO-532 资质证书审核提醒（站内信 + 企微推送 + 24h 去重 + 下架停止）
// Pos: Service/业务层 - alerts 模块下的审核提醒编排
// 维护声明:
//   - 24h 去重规则复用 QualificationExpiryPolicy.shouldRemindToday；
//   - 模板文案沉淀到 QualificationReviewAlertMessage；
//   - 本类只做编排：找证书、找接收人、调通知、更新最后审核提醒时间；
//   - 不修改入参、不抛业务分支异常（用 ScanOutcome 返回值表达）。
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.businessqualification.application.view.QualificationReviewAlertMessage;
import com.xiyu.bid.businessqualification.domain.service.QualificationExpiryPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
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
 * CO-532 资质证书审核提醒编排：扫描 → 过滤 → 模板 → 站内信 + 企微 → 更新最后审核提醒时间。
 * <p>
 * 规则：
 * <ul>
 *   <li>提前天数：固定 90 天（CO-532 需求明确，不读 AlertConfig）</li>
 *   <li>接收人：与到期提醒一致 — 行政人员、投标管理员、投标组长</li>
 *   <li>渠道：站内信（Notification）+ 企微（NotificationDeliveryTaskListener 异步入队）</li>
 *   <li>频次：每张证书每日至多 1 次（lastReviewRemindedAt + 24h 去重）</li>
 *   <li>跳过：下架（status=RETIRED）、审核日期字段缺失、剩余天数超出 [0, 90] 窗口</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QualificationReviewNotificationService {

    /** CO-532 需求：提前 90 天提醒证书审核。 */
    static final int REVIEW_REMINDER_DAYS = 90;

    /** 接收人角色：与到期提醒一致 — 行政人员、投标管理员、投标组长。 */
    static final List<String> RECIPIENT_ROLE_CODES = List.of(
            "bid-administration", "/bidAdmin", "bid-TeamLeader"
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
     * 主入口：扫描即将需要审核的资质，对接收人发送站内信 + 企微推送。
     *
     * @param detailUrlBase 跳转链接基础 URL（null 时使用默认 /knowledge/qualification?id={id}）
     * @return 扫描结果（已发送数、跳过数、命中数）
     */
    @Transactional
    public ScanOutcome runScan(String detailUrlBase) {
        final LocalDateTime now = LocalDateTime.now(clock);
        final LocalDate today = LocalDate.now(clock);

        // 1. 找证书：审核日期 <= today + 90 且未下架
        List<BusinessQualificationEntity> candidates = qualificationJpaRepository
                .findByCertReviewNoteLessThanEqualAndStatusNot(
                        today.plusDays(REVIEW_REMINDER_DAYS), QualificationStatus.RETIRED);
        log.info("[CO-532] scanning {} candidate qualifications (reviewReminderDays={})",
                candidates.size(), REVIEW_REMINDER_DAYS);

        // 2. 找接收人
        List<Long> recipientIds = resolveRecipientUserIds();
        if (recipientIds.isEmpty()) {
            log.warn("[CO-532] no enabled recipient users (admin_staff/bid_admin/bid_lead), aborting dispatch");
            return new ScanOutcome(candidates.size(), 0, candidates.size(), List.of());
        }

        // 3. 解析 system actor（定时任务无登录态，必须显式提供 created_by）
        Long systemActor = systemActorResolver.resolveCached();
        if (systemActor == null) {
            log.warn("[CO-532] system actor unresolved, aborting dispatch to avoid created_by=null");
            return new ScanOutcome(candidates.size(), 0, candidates.size(), List.of());
        }

        int notified = 0;
        int skipped = 0;
        List<NotifiedCert> notifiedCerts = new ArrayList<>();

        for (BusinessQualificationEntity q : candidates) {
            SkipReason reason = shouldSkip(q, today, now);
            if (reason != null) {
                log.debug("[CO-532] skip qualification id={} reason={}", q.getId(), reason);
                skipped++;
                continue;
            }

            long remaining = computeRemainingDays(q.getCertReviewNote(), today);
            String link = buildLink(detailUrlBase, q);
            QualificationReviewAlertMessage msg = QualificationReviewAlertMessage.from(
                    toDomainLike(q), remaining, q.getLevel(), link);

            try {
                notificationApplicationService.createNotification(
                        new CreateNotificationRequest(
                                NotificationType.DEADLINE.name(),
                                "Qualification",
                                q.getId(),
                                msg.title(),
                                msg.body(),
                                buildPayload(q, remaining, link),
                                recipientIds
                        ),
                        systemActor
                );
                q.setLastReviewRemindedAt(now);
                qualificationJpaRepository.save(q);
                notified++;
                notifiedCerts.add(new NotifiedCert(q.getId(), q.getName(), remaining));
            } catch (RuntimeException ex) {
                log.error("[CO-532] failed to dispatch review notification for qualification id={} name={}: {}",
                        q.getId(), q.getName(), ex.getMessage(), ex);
                skipped++;
            }
        }

        log.info("[CO-532] scan done. scanned={} notified={} skipped={} recipients={}",
                candidates.size(), notified, skipped, recipientIds.size());
        return new ScanOutcome(candidates.size(), notified, skipped, notifiedCerts);
    }

    /** 跳过原因枚举（仅做日志和返回值，不抛异常）。 */
    enum SkipReason {
        /** 证书已下架（status=RETIRED）。 */
        RETIRED,
        /** 审核提醒日期字段缺失。 */
        INVALID_REVIEW_DATE,
        /** 剩余天数不在 [0, 90] 窗口内。 */
        OUT_OF_WINDOW,
        /** 24 小时内已提醒过。 */
        DEDUP_24H
    }

    /**
     * 判定是否应跳过：仅做单证书判定，不读数据库。
     * <p>
     * 注意 RETIRED 已在 JPA 查询层排除；此处保留防御性检查以兼容未来调用方。
     */
    SkipReason shouldSkip(BusinessQualificationEntity q, LocalDate today, LocalDateTime now) {
        if (q.getStatus() == QualificationStatus.RETIRED) {
            return SkipReason.RETIRED;
        }
        if (q.getCertReviewNote() == null) {
            return SkipReason.INVALID_REVIEW_DATE;
        }
        long remaining = computeRemainingDays(q.getCertReviewNote(), today);
        if (remaining < 0 || remaining > REVIEW_REMINDER_DAYS) {
            return SkipReason.OUT_OF_WINDOW;
        }
        if (!expiryPolicy.shouldRemindToday(q.getLastReviewRemindedAt(), now)) {
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
            log.error("[CO-532] failed to resolve recipients by role codes {}: {}",
                    RECIPIENT_ROLE_CODES, ex.getMessage(), ex);
            return List.of();
        }
    }

    private static String buildLink(String detailUrlBase, BusinessQualificationEntity q) {
        if (detailUrlBase == null || detailUrlBase.isBlank()) {
            return QualificationReviewAlertMessage.buildDefaultLink(q.getId());
        }
        String trimmed = detailUrlBase.endsWith("/")
                ? detailUrlBase.substring(0, detailUrlBase.length() - 1)
                : detailUrlBase;
        return trimmed + "/knowledge/qualification?id=" + q.getId();
    }

    private static long computeRemainingDays(LocalDate reviewDate, LocalDate today) {
        if (reviewDate == null) {
            return Long.MAX_VALUE;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(today, reviewDate);
    }

    /**
     * 把 entity 临时映射成 domain record 的同形视图（仅用于消息模板，不持久化）。
     * <p>
     * 这样 {@link QualificationReviewAlertMessage#from} 的纯核心签名无需变化。
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
                e.getId(), e.getName(), e.getLevel(), subject, e.getCategory(),
                e.getCertificateNo(), e.getIssuer(), e.getAgency(), e.getAgencyContact(),
                e.getCertScope(), e.getCertReviewNote(), e.getHolderName(),
                validity, policy,
                e.getFileUrl(), e.getAuditLogFileUrl(), e.getRetireReason(), java.util.List.of()
        );
    }

    private static Map<String, Object> buildPayload(
            BusinessQualificationEntity q, long remainingDays, String link) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qualificationId", q.getId());
        payload.put("qualificationName", q.getName());
        payload.put("certificateNo", q.getCertificateNo());
        payload.put("remainingDays", remainingDays);
        payload.put("reviewReminderDays", REVIEW_REMINDER_DAYS);
        payload.put("certReviewNote", q.getCertReviewNote() == null ? null : q.getCertReviewNote().toString());
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
