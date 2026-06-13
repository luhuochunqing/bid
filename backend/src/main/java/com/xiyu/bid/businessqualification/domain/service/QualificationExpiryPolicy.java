package com.xiyu.bid.businessqualification.domain.service;

import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class QualificationExpiryPolicy {

    private static final int DEFAULT_EXPIRING_DAYS = 90;

    /** 24 小时内同证书最多提醒 1 次。 */
    public static final int REMIND_DEDUP_HOURS = 24;

    public QualificationStatus evaluate(ValidityPeriod period, LocalDate today) {
        long remainingDays = period.remainingDays(today);
        if (remainingDays < 0) {
            return QualificationStatus.EXPIRED;
        }
        if (remainingDays <= DEFAULT_EXPIRING_DAYS) {
            return QualificationStatus.EXPIRING;
        }
        return QualificationStatus.VALID;
    }

    public String alertLevel(QualificationStatus status) {
        if (status == QualificationStatus.EXPIRED) {
            return "danger";
        }
        if (status == QualificationStatus.EXPIRING) {
            return "warning";
        }
        return "none";
    }

    /**
     * §4.1.3.8 蓝图通用规则：同证书在 24 小时内至多提醒 1 次。
     * <p>
     * 纯核心：不读数据库、不读时间，只计算两个 LocalDateTime 的差值。
     *
     * @param lastRemindedAt 上次提醒时间（null 表示从未提醒过）
     * @param now 当前时间
     * @return true 表示应再次提醒；false 表示应跳过（24h 内已提醒过）
     */
    public boolean shouldRemindToday(LocalDateTime lastRemindedAt, LocalDateTime now) {
        if (lastRemindedAt == null) {
            return true;
        }
        return !now.isBefore(lastRemindedAt.plusHours(REMIND_DEDUP_HOURS));
    }

    /**
     * 续期判定：剩余有效期是否仍处于配置阈值内。
     * <p>
     * 蓝图 §4.1.3.8 续期后自动停止的语义：续期后 expiryDate 推后，
     * 距离到期的剩余天数 > 配置阈值时，扫描不再命中。
     *
     * @param validityPeriod 证书有效期
     * @param today 当前日期
     * @param alertDays 提前提醒天数
     * @return true 表示剩余天数在阈值内（需提醒）
     */
    public boolean isWithinAlertWindow(ValidityPeriod validityPeriod, LocalDate today, int alertDays) {
        if (validityPeriod == null || validityPeriod.getExpiryDate() == null) {
            return false;
        }
        long remaining = validityPeriod.remainingDays(today);
        return remaining >= 0 && remaining <= alertDays;
    }

    /**
     * 提醒策略是否启用（蓝图 §4.1.3.8 通用规则：续期 / 下架后停止）。
     * <p>
     * 仅依赖 ReminderPolicy.enabled 字段，纯函数。
     */
    public boolean isReminderActive(ReminderPolicy reminderPolicy) {
        return reminderPolicy != null && reminderPolicy.isEnabled();
    }
}
