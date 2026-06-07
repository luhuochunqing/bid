// Input: CalendarEvent实体
// Output: CalendarEvent数据传输对象
// Pos: DTO/数据传输对象
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.calendar.dto;

import com.xiyu.bid.calendar.entity.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日历事件DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventDTO {

    private Long id;
    private LocalDate eventDate;
    private EventType eventType;
    private String title;
    private String description;
    private Long projectId;
    private Boolean isUrgent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
