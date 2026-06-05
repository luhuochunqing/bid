package com.xiyu.bid.tenderreminder.dto;

import com.xiyu.bid.tenderreminder.entity.ReminderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提醒设置响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderSettingDTO {

    private Long id;

    private Long tenderId;

    private String tenderTitle;

    private ReminderType reminderType;

    private String reminderTypeDesc;

    private Integer remindBeforeHours;

    private List<ReminderTarget> reminderTargets;

    private Boolean enabled;

    private LocalDateTime lastNotifiedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 通知目标
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReminderTarget {
        private Long userId;
        private String userName;
        private String wecomUserId;
    }

    /**
     * 获取提醒类型描述
     */
    public String getReminderTypeDesc() {
        if (reminderType == null) {
            return null;
        }
        return switch (reminderType) {
            case REGISTRATION_DEADLINE -> "报名截止提醒";
            case BID_OPENING -> "开标提醒";
        };
    }
}
