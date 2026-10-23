package com.xiyu.bid.tenderreminder.job;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tenderreminder.entity.ReminderType;

import java.time.LocalDateTime;

/**
 * 标讯提醒企微文案。标题只放类型和标讯名，正文放截止时间，避免标题超 128 字符截断正文。
 */
final class TenderReminderMessages {

    private TenderReminderMessages() {
    }

    static String title(Tender tender, ReminderType reminderType) {
        return switch (reminderType) {
            case REGISTRATION_DEADLINE -> "【报名截止提醒】" + tender.getTitle();
            case BID_OPENING -> "【开标提醒】" + tender.getTitle();
        };
    }

    static String content(Tender tender, ReminderType reminderType, Integer hoursBefore) {
        String deadlineType = switch (reminderType) {
            case REGISTRATION_DEADLINE -> "报名截止";
            case BID_OPENING -> "开标";
        };
        LocalDateTime deadline = switch (reminderType) {
            case REGISTRATION_DEADLINE -> tender.getRegistrationDeadline();
            case BID_OPENING -> tender.getBidOpeningTime();
        };
        String deadlineStr = deadline != null ? deadline.toString() : "未设置";
        int hours = hoursBefore != null ? hoursBefore : 72;
        return String.format("""
                %s提醒：标讯「%s」即将%s

                %s时间：%s
                提前提醒：%d小时

                请及时处理！
                """,
                deadlineType, tender.getTitle(), deadlineType,
                deadlineType, deadlineStr, hours);
    }
}
