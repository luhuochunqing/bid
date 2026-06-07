package com.xiyu.bid.tenderreminder.dto;

import com.xiyu.bid.tenderreminder.entity.ReminderType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建提醒设置请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReminderRequest {

    @NotNull(message = "提醒类型不能为空")
    private ReminderType reminderType;

    @Min(value = 1, message = "提前提醒小时数至少为1")
    @Max(value = 168, message = "提前提醒小时数最多为168(7天)")
    @Builder.Default
    private Integer remindBeforeHours = 24;

    @NotNull(message = "通知对象不能为空")
    @Size(min = 1, message = "至少需要选择一个通知对象")
    private List<ReminderTargetDTO> reminderTargets;

    @Builder.Default
    private Boolean enabled = true;

    /**
     * 通知目标DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReminderTargetDTO {
        @NotNull(message = "用户ID不能为空")
        private Long userId;

        private String userName;

        private String wecomUserId;
    }
}
