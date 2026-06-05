// Input: User input
// Output: CalendarEvent创建请求
// Pos: DTO/数据传输对象
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.calendar.dto;

import com.xiyu.bid.calendar.entity.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 创建日历事件请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventCreateRequest {

    @NotNull(message = "Event date is required")
    private LocalDate eventDate;

    @NotNull(message = "Event type is required")
    private EventType eventType;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private Long projectId;

    private Boolean isUrgent;
}
