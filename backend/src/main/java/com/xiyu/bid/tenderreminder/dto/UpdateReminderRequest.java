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
 * 更新提醒设置请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReminderRequest {

    @Min(value = 1, message = "提前提醒小时数至少为1")
    @Max(value = 168, message = "提前提醒小时数最多为168(7天)")
    private Integer remindBeforeHours;

    private List<CreateReminderRequest.ReminderTargetDTO> reminderTargets;

    private Boolean enabled;
}
