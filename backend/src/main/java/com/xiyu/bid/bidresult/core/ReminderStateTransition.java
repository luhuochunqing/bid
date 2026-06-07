package com.xiyu.bid.bidresult.core;

import com.xiyu.bid.bidresult.entity.BidResultReminder;

import java.time.LocalDateTime;

public final class ReminderStateTransition {

    private ReminderStateTransition() {
    }

    public static BidResultReminder.ReminderStatus ensurePending(BidResultReminder.ReminderStatus current) {
        return current == BidResultReminder.ReminderStatus.UPLOADED
                ? BidResultReminder.ReminderStatus.UPLOADED
                : BidResultReminder.ReminderStatus.PENDING;
    }

    public static ReminderSnapshot send(String comment, LocalDateTime now) {
        return new ReminderSnapshot(
                BidResultReminder.ReminderStatus.REMINDED,
                now,
                normalizeComment(comment, "已发送上传提醒"),
                null,
                null,
                null
        );
    }

    public static ReminderSnapshot upload(Long documentId, Long operatorId, LocalDateTime now) {
        return new ReminderSnapshot(
                BidResultReminder.ReminderStatus.UPLOADED,
                now,
                "资料已上传",
                documentId,
                operatorId,
                now
        );
    }

    public static ReminderSnapshot revertAfterAttachmentRemoved(
            BidResultReminder.ReminderStatus current,
            String previousComment,
            LocalDateTime remindTime
    ) {
        BidResultReminder.ReminderStatus target =
                current == BidResultReminder.ReminderStatus.REMINDED
                        ? BidResultReminder.ReminderStatus.REMINDED
                        : BidResultReminder.ReminderStatus.PENDING;
        return new ReminderSnapshot(
                target,
                remindTime,
                normalizeComment(previousComment, "待上传资料"),
                null,
                null,
                null
        );
    }

    public static String normalizeComment(String comment, String fallback) {
        if (comment == null || comment.isBlank()) {
            return fallback;
        }
        return comment.trim();
    }

    public record ReminderSnapshot(
            BidResultReminder.ReminderStatus status,
            LocalDateTime remindTime,
            String comment,
            Long attachmentDocumentId,
            Long uploadedBy,
            LocalDateTime uploadedAt
    ) {
    }
}

