package com.xiyu.bid.tenderreminder.dto;

import com.xiyu.bid.tenderreminder.entity.ReminderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 提醒日志DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderLogDTO {

    private Long id;

    private Long reminderSettingId;

    private Long tenderId;

    private ReminderType reminderType;

    private Long recipientUserId;

    private String recipientUserName;

    private String recipientWecomUserId;

    private String status;

    private String statusDesc;

    private String errorMessage;

    private LocalDateTime sentAt;

    /**
     * 获取状态描述
     */
    public String getStatusDesc() {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "SENT" -> "已发送";
            case "FAILED" -> "发送失败";
            case "SKIPPED" -> "已跳过";
            default -> status;
        };
    }
}
