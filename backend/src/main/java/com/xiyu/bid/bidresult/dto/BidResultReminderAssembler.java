package com.xiyu.bid.bidresult.dto;

import com.xiyu.bid.bidresult.entity.BidResultReminder;

public final class BidResultReminderAssembler {

    private BidResultReminderAssembler() {
    }

    public static BidResultReminderDTO toDto(BidResultReminder entity) {
        if (entity == null) {
            return null;
        }
        return BidResultReminderDTO.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .projectName(entity.getProjectName())
                .ownerId(entity.getOwnerId())
                .ownerName(entity.getOwnerName())
                .reminderType(entity.getReminderType())
                .status(entity.getStatus())
                .remindTime(entity.getRemindTime())
                .lastReminderComment(entity.getLastReminderComment())
                .lastResultId(entity.getLastResultId())
                .attachmentDocumentId(entity.getAttachmentDocumentId())
                .uploadedBy(entity.getUploadedBy())
                .uploadedAt(entity.getUploadedAt())
                .build();
    }
}
