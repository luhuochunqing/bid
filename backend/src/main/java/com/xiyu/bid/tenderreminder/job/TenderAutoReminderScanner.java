package com.xiyu.bid.tenderreminder.job;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.notification.outbound.service.NotificationDeliveryResult;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tenderreminder.domain.TenderReminderPolicy;
import com.xiyu.bid.tenderreminder.entity.ReminderType;
import com.xiyu.bid.tenderreminder.entity.TenderReminderLog;
import com.xiyu.bid.tenderreminder.entity.TenderReminderSetting;
import com.xiyu.bid.tenderreminder.repository.TenderReminderLogRepository;
import com.xiyu.bid.tenderreminder.repository.TenderReminderSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 没有手工提醒设置的标讯，按报名截止和开标时间自动提醒。
 * 接收人每次扫描实时解析，不写入 tender_reminder_settings。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenderAutoReminderScanner {

    private static final TenderReminderSetting AUTO_RULE = TenderReminderSetting.builder()
            .enabled(true)
            .remindBeforeHours(TenderReminderPolicy.getDefaultRemindBeforeHours())
            .build();

    private final TenderRepository tenderRepository;
    private final TenderReminderSettingRepository settingRepository;
    private final TenderReminderLogRepository logRepository;
    private final NotificationRecipientResolver recipientResolver;
    private final WeComPushService weComPushService;

    /** 独立事务：这里的数据库失败不能回滚同一小时已经写出的手工提醒。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ScanResult scan(LocalDateTime now) {
        List<Long> recipients = recipientResolver.getAdminUserIds();
        if (recipients == null || recipients.isEmpty()) {
            log.warn("自动标讯提醒没有接收人，跳过本轮");
            return ScanResult.empty();
        }
        Counts counts = new Counts();
        LocalDateTime windowEnd = now.plusHours(TenderReminderPolicy.getDefaultRemindBeforeHours());
        scanType(ReminderType.REGISTRATION_DEADLINE, now, recipients,
                orEmpty(tenderRepository.findTendersByRegistrationDeadlineBetween(now, windowEnd)),
                Tender::getRegistrationDeadline, counts);
        scanType(ReminderType.BID_OPENING, now, recipients,
                orEmpty(tenderRepository.findTendersByBidOpeningTimeBetween(now, windowEnd)),
                Tender::getBidOpeningTime, counts);
        return new ScanResult(counts.processed, counts.sent, counts.skipped, counts.failed);
    }

    private void scanType(
            ReminderType type,
            LocalDateTime now,
            List<Long> recipients,
            List<Tender> tenders,
            Function<Tender, LocalDateTime> deadlineOf,
            Counts counts) {
        for (Tender tender : tenders) {
            if (tender == null || tender.getId() == null) {
                continue;
            }
            counts.processed++;
            LocalDateTime deadline = deadlineOf.apply(tender);
            if (!TenderReminderPolicy.shouldSendReminder(AUTO_RULE, now, deadline, null)) {
                continue;
            }
            if (settingRepository.existsByTenderIdAndReminderType(tender.getId(), type)) {
                continue;
            }
            Map<Long, LocalDateTime> lastSent = lastSentByRecipient(tender.getId(), type);
            for (Long userId : recipients) {
                if (!TenderReminderPolicy.shouldSendReminder(AUTO_RULE, now, deadline, lastSent.get(userId))) {
                    counts.skipped++;
                    continue;
                }
                deliver(tender, type, userId, now, counts);
            }
        }
    }

    private Map<Long, LocalDateTime> lastSentByRecipient(Long tenderId, ReminderType type) {
        Map<Long, LocalDateTime> lastSent = new HashMap<>();
        List<TenderReminderLog> logs = logRepository.findByTenderIdOrderBySentAtDesc(tenderId);
        if (logs == null) {
            return lastSent;
        }
        for (TenderReminderLog row : logs) {
            if (row.getReminderType() != type || !"SENT".equals(row.getStatus()) || row.getRecipientUserId() == null) {
                continue;
            }
            lastSent.putIfAbsent(row.getRecipientUserId(), row.getSentAt());
        }
        return lastSent;
    }

    private void deliver(Tender tender, ReminderType type, Long userId, LocalDateTime now, Counts counts) {
        int hours = TenderReminderPolicy.getDefaultRemindBeforeHours();
        NotificationCreatedEvent event = new NotificationCreatedEvent(
                null,
                List.of(userId),
                "TENDER_REMINDER",
                TenderReminderMessages.title(tender, type),
                TenderReminderMessages.content(tender, type, hours),
                "TENDER",
                tender.getId(),
                null);
        try {
            NotificationDeliveryResult result = weComPushService.pushForRecipient(event, userId);
            if (result != null && result.successful() && !result.skipped()) {
                logRepository.save(TenderReminderLog.builder()
                        .tenderId(tender.getId())
                        .reminderType(type)
                        .recipientUserId(userId)
                        .status("SENT")
                        .sentAt(now)
                        .build());
                counts.sent++;
                return;
            }
            if (result != null && result.skipped()) {
                log.warn("自动标讯提醒跳过: tenderId={}, type={}, userId={}, reason={}",
                        tender.getId(), type, userId, result.message());
                counts.skipped++;
                return;
            }
            log.warn("自动标讯提醒发送失败: tenderId={}, type={}, userId={}, code={}, error={}",
                    tender.getId(), type, userId, result == null ? null : result.errcode(),
                    result == null ? "empty delivery result" : result.message());
            counts.failed++;
        } catch (RuntimeException ex) {
            log.warn("自动标讯提醒发送失败: tenderId={}, type={}, userId={}, error={}",
                    tender.getId(), type, userId, ex.getMessage());
            counts.failed++;
        }
    }

    private static List<Tender> orEmpty(List<Tender> tenders) {
        return tenders == null ? List.of() : tenders;
    }

    public record ScanResult(int processed, int sent, int skipped, int failed) {
        public static ScanResult empty() {
            return new ScanResult(0, 0, 0, 0);
        }
    }

    private static final class Counts {
        private int processed;
        private int sent;
        private int skipped;
        private int failed;
    }
}
