package com.xiyu.bid.tenderreminder.domain;

import com.xiyu.bid.tenderreminder.dto.CreateReminderRequest;
import com.xiyu.bid.tenderreminder.entity.ReminderType;
import com.xiyu.bid.tenderreminder.entity.TenderReminderSetting;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 提醒设置领域策略（纯核心）
 * 不依赖数据库、API、框架
 */
public record TenderReminderPolicy() {

    /**
     * 计算提醒触发时间
     */
    public static LocalDateTime calculateRemindAt(LocalDateTime deadline, int remindBeforeHours) {
        if (deadline == null) {
            return null;
        }
        return deadline.minusHours(remindBeforeHours);
    }

    /**
     * 检查是否需要发送提醒
     * 条件：启用 + 未超过截止时间 + 未超过提前时间 + 未发送过
     */
    public static boolean shouldSendReminder(
            TenderReminderSetting setting,
            LocalDateTime currentTime,
            LocalDateTime deadline,
            LocalDateTime lastNotifiedAt) {

        if (setting == null || !Boolean.TRUE.equals(setting.getEnabled())) {
            return false;
        }

        if (deadline == null) {
            return false;
        }

        int hoursBefore = setting.getRemindBeforeHours() != null ? setting.getRemindBeforeHours() : 24;
        LocalDateTime remindAt = calculateRemindAt(deadline, hoursBefore);

        if (remindAt == null) {
            return false;
        }

        // 检查当前时间是否已到达提醒时间点
        if (currentTime.isBefore(remindAt)) {
            return false;
        }

        // 检查是否已发送过（避免重复发送）
        if (lastNotifiedAt != null) {
            return false;
        }

        // 检查是否已过截止时间
        return !currentTime.isAfter(deadline);
    }

    /**
     * 验证提前提醒小时数
     */
    public static boolean isValidRemindBeforeHours(Integer hours) {
        return hours != null && hours >= 1 && hours <= 168;
    }

    /**
     * 检查是否有通知目标
     */
    public static boolean hasReminderTargets(CreateReminderRequest.ReminderTargetDTO[] targets) {
        return targets != null && targets.length > 0;
    }

    /**
     * 获取默认提前提醒小时数
     */
    public static int getDefaultRemindBeforeHours() {
        return 24;
    }

    /**
     * 获取有效的提前提醒小时数
     */
    public static int getEffectiveRemindBeforeHours(Integer hours) {
        return isValidRemindBeforeHours(hours) ? hours : getDefaultRemindBeforeHours();
    }
}
