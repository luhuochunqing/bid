package com.xiyu.bid.bidresult.dto;

import com.xiyu.bid.bidresult.entity.BidResultReminder;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BidResultReminderDTO {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long ownerId;
    private String ownerName;
    private BidResultReminder.ReminderType reminderType;
    private BidResultReminder.ReminderStatus status;
    private LocalDateTime remindTime;
    private String lastReminderComment;
    private Long lastResultId;
    private Long attachmentDocumentId;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
}
