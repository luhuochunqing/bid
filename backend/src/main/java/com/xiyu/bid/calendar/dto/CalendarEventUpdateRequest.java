// Input: User input
// Output: CalendarEvent更新请求
// Pos: DTO/数据传输对象
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.calendar.dto;

import com.xiyu.bid.calendar.entity.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 更新日历事件请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventUpdateRequest {

    private LocalDate eventDate;
    private EventType eventType;
    private String title;
    private String description;
    private Long projectId;
    private Boolean isUrgent;
}
