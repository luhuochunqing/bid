package com.xiyu.bid.tenderreminder.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tenderreminder.entity.ReminderType;
import com.xiyu.bid.tenderreminder.entity.TenderReminderLog;
import com.xiyu.bid.tenderreminder.entity.TenderReminderSetting;
import com.xiyu.bid.tenderreminder.repository.TenderReminderLogRepository;
import com.xiyu.bid.tenderreminder.repository.TenderReminderSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标讯提醒定时任务
 * 每小时执行一次，检查并发送到期的提醒
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenderReminderJob {

    private final TenderReminderSettingRepository settingRepository;
    private final TenderReminderLogRepository logRepository;
    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;
    private final WeComPushService weComPushService;
    private final ObjectMapper objectMapper;

    /**
     * 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void processReminders() {
        log.info("开始处理标讯提醒任务");
        LocalDateTime now = LocalDateTime.now();

        int totalProcessed = 0;
        int totalSent = 0;
        int totalSkipped = 0;
        int totalFailed = 0;

        // 处理报名截止提醒
        ProcessResult regResult = processReminderType(ReminderType.REGISTRATION_DEADLINE, now,
                (setting, tender) -> tender.getRegistrationDeadline());
        totalProcessed += regResult.processed;
        totalSent += regResult.sent;
        totalSkipped += regResult.skipped;
        totalFailed += regResult.failed;

        // 处理开标提醒
        ProcessResult bidResult = processReminderType(ReminderType.BID_OPENING, now,
                (setting, tender) -> tender.getBidOpeningTime());
        totalProcessed += bidResult.processed;
        totalSent += bidResult.sent;
        totalSkipped += bidResult.skipped;
        totalFailed += bidResult.failed;

        log.info("标讯提醒任务完成: processed={}, sent={}, skipped={}, failed={}",
                totalProcessed, totalSent, totalSkipped, totalFailed);
    }

    private ProcessResult processReminderType(
            ReminderType reminderType,
            LocalDateTime currentTime,
            DeadlineExtractor extractor) {

        int processed = 0;
        int sent = 0;
        int skipped = 0;
        int failed = 0;

        List<TenderReminderSetting> settings = settingRepository.findByEnabledTrue();

        for (TenderReminderSetting setting : settings) {
            if (setting.getReminderType() != reminderType) {
                continue;
            }

            processed++;

            try {
                Tender tender = tenderRepository.findById(setting.getTenderId()).orElse(null);
                if (tender == null) {
                    log.warn("标讯不存在: tenderId={}", setting.getTenderId());
                    skipped++;
                    continue;
                }

                LocalDateTime deadline = extractor.extract(setting, tender);
                if (deadline == null) {
                    log.debug("标讯无截止时间: tenderId={}, type={}", tender.getId(), reminderType);
                    skipped++;
                    continue;
                }

                // 检查是否需要发送提醒
                if (!shouldSendReminder(setting, currentTime, deadline)) {
                    log.debug("提醒条件不满足: settingId={}, deadline={}", setting.getId(), deadline);
                    skipped++;
                    continue;
                }

                // 解析通知目标
                List<ReminderTargetInfo> targets = parseReminderTargets(setting.getReminderTargets());
                if (targets.isEmpty()) {
                    log.warn("提醒设置无通知目标: settingId={}", setting.getId());
                    skipped++;
                    continue;
                }

                // 发送提醒
                boolean hasError = false;
                for (ReminderTargetInfo target : targets) {
                    try {
                        sendReminder(setting, tender, reminderType, target);
                        sent++;
                    } catch (RuntimeException e) {
                        log.error("发送提醒失败: settingId={}, userId={}, error={}",
                                setting.getId(), target.userId(), e.getMessage());
                        hasError = true;
                    }
                }

                if (hasError) {
                    failed++;
                }

                // 更新最后通知时间
                setting.setLastNotifiedAt(currentTime);
                settingRepository.save(setting);

            } catch (RuntimeException e) {
                log.error("处理提醒设置失败: settingId={}, error={}", setting.getId(), e.getMessage());
                failed++;
            }
        }

        return new ProcessResult(processed, sent, skipped, failed);
    }

    private boolean shouldSendReminder(TenderReminderSetting setting, LocalDateTime currentTime, LocalDateTime deadline) {
        // 已发送过，不再发送
        if (setting.getLastNotifiedAt() != null) {
            return false;
        }

        int hoursBefore = setting.getRemindBeforeHours() != null ? setting.getRemindBeforeHours() : 24;
        LocalDateTime remindAt = deadline.minusHours(hoursBefore);

        // 当前时间已达到提醒时间点
        return !currentTime.isBefore(remindAt) && !currentTime.isAfter(deadline);
    }

    private List<ReminderTargetInfo> parseReminderTargets(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ReminderTargetInfo>>() {});
        } catch (JsonProcessingException e) {
            log.error("解析通知目标JSON失败: {}", json, e);
            return List.of();
        }
    }

    private void sendReminder(
            TenderReminderSetting setting,
            Tender tender,
            ReminderType reminderType,
            ReminderTargetInfo target) {

        // 构建消息内容
        String title = buildReminderTitle(tender, reminderType);
        String content = buildReminderContent(tender, reminderType, setting.getRemindBeforeHours());

        // 直接调用企微推送
        NotificationCreatedEvent event = new NotificationCreatedEvent(
                null,
                List.of(target.userId()),
                "TENDER_REMINDER",
                title + "\n" + content,
                "TENDER",
                tender.getId()
        );
        weComPushService.pushForRecipient(event, target.userId());

        // 记录发送日志
        TenderReminderLog reminderLog = TenderReminderLog.builder()
                .reminderSettingId(setting.getId())
                .tenderId(tender.getId())
                .reminderType(reminderType)
                .recipientUserId(target.userId())
                .recipientWecomUserId(target.wecomUserId())
                .status("SENT")
                .sentAt(LocalDateTime.now())
                .build();
        logRepository.save(reminderLog);

        TenderReminderJob.log.info("发送提醒成功: tenderId={}, userId={}, type={}", tender.getId(), target.userId(), reminderType);
    }

    private String buildReminderTitle(Tender tender, ReminderType reminderType) {
        return switch (reminderType) {
            case REGISTRATION_DEADLINE -> "【报名截止提醒】" + tender.getTitle();
            case BID_OPENING -> "【开标提醒】" + tender.getTitle();
        };
    }

    private String buildReminderContent(Tender tender, ReminderType reminderType, Integer hoursBefore) {
        String deadlineType = switch (reminderType) {
            case REGISTRATION_DEADLINE -> "报名截止";
            case BID_OPENING -> "开标";
        };

        LocalDateTime deadline = switch (reminderType) {
            case REGISTRATION_DEADLINE -> tender.getRegistrationDeadline();
            case BID_OPENING -> tender.getBidOpeningTime();
        };

        String deadlineStr = deadline != null ? deadline.toString() : "未设置";
        int hours = hoursBefore != null ? hoursBefore : 24;

        return String.format("""
                %s提醒：标讯「%s」即将%s
                
                %s时间：%s
                提前提醒：%d小时
                
                请及时处理！
                """,
                deadlineType, tender.getTitle(), deadlineType,
                deadlineType, deadlineStr, hours);
    }

    @FunctionalInterface
    private interface DeadlineExtractor {
        LocalDateTime extract(TenderReminderSetting setting, Tender tender);
    }

    private record ReminderTargetInfo(Long userId, String userName, String wecomUserId) {}

    private record ProcessResult(int processed, int sent, int skipped, int failed) {}
}
