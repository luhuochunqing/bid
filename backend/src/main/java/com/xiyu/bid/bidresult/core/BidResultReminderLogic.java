package com.xiyu.bid.bidresult.core;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.entity.BidResultReminder;

import java.time.LocalDateTime;

/**
 * Compatibility wrapper for existing tests.
 */
public final class BidResultReminderLogic {

    private BidResultReminderLogic() {
    }

    public static BidResultReminder.ReminderType determineType(BidResultFetchResult.Result result) {
        return ReminderTypeResolver.resolve(result);
    }

    public static BidResultReminder calculateReminderState(
            BidResultFetchResult fetchResult,
            BidResultReminder existingReminder,
            Long ownerId,
            String ownerName,
            String comment,
            Long operatorId,
            String operatorName,
            LocalDateTime currentTime,
            boolean isSendAction
    ) {
        BidResultReminder reminder = existingReminder == null
                ? BidResultReminder.builder()
                .projectId(fetchResult.getProjectId())
                .projectName(fetchResult.getProjectName())
                .ownerId(ownerId)
                .ownerName(ownerName)
                .reminderType(determineType(fetchResult.getResult()))
                .createdBy(operatorId)
                .createdByName(operatorName)
                .build()
                : existingReminder;

        reminder.setStatus(ReminderStateTransition.ensurePending(reminder.getStatus()));
        reminder.setRemindTime(reminder.getRemindTime() == null ? currentTime : reminder.getRemindTime());
        reminder.setLastReminderComment(ReminderStateTransition.normalizeComment(comment, "待上传中标资料"));
        reminder.setLastResultId(fetchResult.getId());
        if (reminder.getCreatedByName() == null || reminder.getCreatedByName().isBlank()) {
            reminder.setCreatedByName(operatorName);
        }
        if (isSendAction) {
            ReminderStateTransition.ReminderSnapshot sent = ReminderStateTransition.send(comment, currentTime);
            reminder.setStatus(sent.status());
            reminder.setRemindTime(sent.remindTime());
            reminder.setLastReminderComment(sent.comment());
        }
        return reminder;
    }
}
