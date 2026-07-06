// Input: AccountBorrowApplicationRepository, PlatformAccountRepository, UserRepository, NotificationApplicationService, NotificationRepository
// Output: CO-523 账户管理 — 平台账号借用到期/逾期/待审批提醒编排
// Pos: Service/业务层 — 定时扫描与通知编排；纯规则下沉到 determineReminderType 静态方法
package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.NotificationType;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountBorrowExpiryScanService {

    static final String SOURCE_PENDING = "ACCOUNT_BORROW_PENDING";
    static final String SOURCE_EXPIRING = "ACCOUNT_BORROW_EXPIRING";
    static final String SOURCE_OVERDUE = "ACCOUNT_BORROW_OVERDUE";

    /** 借用归还即将到期阈值：距离预计归还时间 ≤ 2 天。 */
    private static final int EXPIRING_THRESHOLD_DAYS = 2;

    private static final String BID_ADMIN_ROLE_CODE = "/bidAdmin";

    private final AccountBorrowApplicationRepository applicationRepository;
    private final PlatformAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final NotificationApplicationService notificationService;
    private final NotificationRepository notificationRepository;

    /**
     * 扫描并发送平台账号借用相关提醒。
     *
     * @param now 当前时间（测试注入用）
     * @return 实际发送的通知数量
     */
    @Transactional
    public int scan(LocalDateTime now) {
        int sent = 0;
        sent += scanPending(now);
        sent += scanBorrowed(now);
        log.info("[AccountBorrowExpiry] scan completed at {}. total sent={}", now, sent);
        return sent;
    }

    private int scanPending(LocalDateTime now) {
        List<AccountBorrowApplication> pending = applicationRepository.findByStatus(BorrowStatus.PENDING_APPROVAL);
        if (pending.isEmpty()) {
            return 0;
        }

        int sent = 0;
        for (AccountBorrowApplication app : pending) {
            ReminderType type = determineReminderType(app, now);
            if (type != ReminderType.PENDING_APPROVAL) {
                continue;
            }
            if (alreadyReminded(SOURCE_PENDING, app.getId(), now)) {
                log.debug("[AccountBorrowExpiry] skip pending reminder for app id={}: reminded within 24h", app.getId());
                continue;
            }
            String platformName = resolvePlatformName(app.getAccountId());
            List<Long> recipients = List.of(app.getCustodianId());
            if (sendNotification(app, type, platformName, recipients, now)) {
                sent++;
            }
        }
        return sent;
    }

    private int scanBorrowed(LocalDateTime now) {
        List<AccountBorrowApplication> borrowed = applicationRepository.findByStatus(BorrowStatus.BORROWED);
        if (borrowed.isEmpty()) {
            return 0;
        }

        int sent = 0;
        for (AccountBorrowApplication app : borrowed) {
            ReminderType type = determineReminderType(app, now);
            if (type == null || type == ReminderType.PENDING_APPROVAL) {
                continue;
            }
            if (isAccountDisabled(app.getAccountId())) {
                log.debug("[AccountBorrowExpiry] skip borrowed reminder for app id={}: account disabled", app.getId());
                continue;
            }
            String sourceType = sourceType(type);
            if (alreadyReminded(sourceType, app.getId(), now)) {
                log.debug("[AccountBorrowExpiry] skip {} reminder for app id={}: reminded within 24h", type, app.getId());
                continue;
            }
            String platformName = resolvePlatformName(app.getAccountId());
            List<Long> recipients = resolveBorrowedRecipients(app, type);
            if (sendNotification(app, type, platformName, recipients, now)) {
                sent++;
            }
        }
        return sent;
    }

    /**
     * 纯核心判断：根据申请状态和预计归还时间，决定是否需要提醒及提醒类型。
     * 无副作用、不依赖框架、不读数据库。
     */
    static ReminderType determineReminderType(AccountBorrowApplication app, LocalDateTime now) {
        BorrowStatus status = app.getStatus();
        if (status == BorrowStatus.PENDING_APPROVAL) {
            return ReminderType.PENDING_APPROVAL;
        }
        if (status != BorrowStatus.BORROWED) {
            return null;
        }
        LocalDateTime expectedReturnAt = app.getExpectedReturnAt();
        if (expectedReturnAt == null) {
            return null;
        }
        long daysUntil = ChronoUnit.DAYS.between(now.toLocalDate(), expectedReturnAt.toLocalDate());
        if (daysUntil < 0) {
            return ReminderType.OVERDUE;
        }
        if (daysUntil <= EXPIRING_THRESHOLD_DAYS) {
            return ReminderType.EXPIRING_SOON;
        }
        return null;
    }

    private boolean isAccountDisabled(Long accountId) {
        Optional<PlatformAccount> account = accountRepository.findById(accountId);
        return account.map(a -> a.getStatus() == AccountStatus.DISABLED).orElse(true);
    }

    private String resolvePlatformName(Long accountId) {
        return accountRepository.findById(accountId)
                .map(PlatformAccount::getAccountName)
                .orElse("未知平台");
    }

    private List<Long> resolveBorrowedRecipients(AccountBorrowApplication app, ReminderType type) {
        List<Long> recipients = new ArrayList<>();
        recipients.add(app.getApplicantId());
        recipients.add(app.getCustodianId());
        if (type == ReminderType.OVERDUE) {
            List<Long> bidAdmins = userRepository.findEnabledByRoleProfileCodes(List.of(BID_ADMIN_ROLE_CODE)).stream()
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            recipients.addAll(bidAdmins);
        }
        return recipients.stream().distinct().toList();
    }

    private boolean sendNotification(AccountBorrowApplication app, ReminderType type,
                                     String platformName, List<Long> recipients, LocalDateTime now) {
        if (recipients == null || recipients.isEmpty()) {
            log.warn("[AccountBorrowExpiry] no recipients for app id={} type={}", app.getId(), type);
            return false;
        }
        try {
            CreateNotificationRequest request = buildRequest(app, type, platformName, recipients, now);
            notificationService.createNotification(request, 0L);
            return true;
        } catch (RuntimeException ex) {
            log.error("[AccountBorrowExpiry] failed to send {} reminder for app id={}: {}",
                    type, app.getId(), ex.getMessage(), ex);
            return false;
        }
    }

    private CreateNotificationRequest buildRequest(AccountBorrowApplication app, ReminderType type,
                                                   String platformName, List<Long> recipients, LocalDateTime now) {
        String title;
        String body;
        LocalDateTime expectedReturnAt = app.getExpectedReturnAt();
        long remainingDays = expectedReturnAt == null
                ? 0
                : ChronoUnit.DAYS.between(now.toLocalDate(), expectedReturnAt.toLocalDate());

        switch (type) {
            case PENDING_APPROVAL -> {
                title = String.format("【账号借用申请待审批】%s 提交了账号借用申请", platformName);
                body = String.format(
                        "您好，有新的账号借用申请待您审批：\n" +
                        "* 借用平台：%s\n" +
                        "* 借用目的：%s\n" +
                        "* 预计归还日期：%s\n" +
                        "* 申请人 ID：%d\n" +
                        "\n请及时登录系统处理。",
                        platformName,
                        defaultString(app.getPurpose()),
                        expectedReturnAt == null ? "未填写" : expectedReturnAt.toLocalDate().toString(),
                        app.getApplicantId()
                );
            }
            case EXPIRING_SOON -> {
                title = String.format("【账号归还提醒】%s 借用的账号将于 %d 天后到归还日期", platformName, remainingDays);
                body = String.format(
                        "您好，您借用的平台账号即将到归还日期，请及时安排归还：\n" +
                        "* 借用平台：%s\n" +
                        "* 借用时间：%s\n" +
                        "* 预计归还日期：%s\n" +
                        "* 剩余天数：%d 天\n" +
                        "* **归还时请联系绑定联系人办理改密手续**\n" +
                        "\n[查看借用详情 →]",
                        platformName,
                        app.getApprovedAt() == null ? "未知" : app.getApprovedAt().toLocalDate().toString(),
                        expectedReturnAt.toLocalDate().toString(),
                        remainingDays
                );
            }
            case OVERDUE -> {
                title = String.format("【账号借用已逾期】%s 借用的账号已逾期 %d 天", platformName, Math.abs(remainingDays));
                body = String.format(
                        "您好，您借用的平台账号已逾期未归还，请立即处理：\n" +
                        "* 借用平台：%s\n" +
                        "* 借用时间：%s\n" +
                        "* 预计归还日期：%s\n" +
                        "* 逾期天数：%d 天\n" +
                        "* **归还时请联系绑定联系人办理改密手续**\n" +
                        "\n[查看借用详情 →]",
                        platformName,
                        app.getApprovedAt() == null ? "未知" : app.getApprovedAt().toLocalDate().toString(),
                        expectedReturnAt.toLocalDate().toString(),
                        Math.abs(remainingDays)
                );
            }
            default -> throw new IllegalStateException("Unexpected reminder type: " + type);
        }

        return new CreateNotificationRequest(
                NotificationType.DEADLINE.name(),
                sourceType(type),
                app.getId(),
                title,
                body,
                buildPayload(app, type, platformName, remainingDays),
                recipients
        );
    }

    private Map<String, Object> buildPayload(AccountBorrowApplication app, ReminderType type,
                                             String platformName, long remainingDays) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicationId", app.getId());
        payload.put("accountId", app.getAccountId());
        payload.put("platformName", platformName);
        payload.put("reminderType", type.name());
        payload.put("remainingDays", remainingDays);
        payload.put("expectedReturnAt", app.getExpectedReturnAt() == null ? null : app.getExpectedReturnAt().toString());
        return payload;
    }

    private boolean alreadyReminded(String sourceType, Long applicationId, LocalDateTime now) {
        return notificationRepository.existsBySourceEntityTypeAndSourceEntityIdAndCreatedAtAfter(
                sourceType, applicationId, now.minusDays(1));
    }

    private static String sourceType(ReminderType type) {
        return switch (type) {
            case PENDING_APPROVAL -> SOURCE_PENDING;
            case EXPIRING_SOON -> SOURCE_EXPIRING;
            case OVERDUE -> SOURCE_OVERDUE;
        };
    }

    private static String defaultString(String value) {
        return value == null || value.isBlank() ? "未填写" : value;
    }

    /** 提醒类型。 */
    enum ReminderType {
        PENDING_APPROVAL,
        EXPIRING_SOON,
        OVERDUE
    }
}
