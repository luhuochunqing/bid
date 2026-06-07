// Input: EventType枚举
// Output: 日历事件实体定义
// Pos: Entity/实体层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.calendar.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日历事件实体
 * 管理系统中的各种日历事件和提醒
 */
@Entity
@Table(name = "calendar_events", indexes = {
    @Index(name = "idx_event_date", columnList = "event_date"),
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_calendar_project_id", columnList = "project_id"),
    @Index(name = "idx_urgent", columnList = "is_urgent"),
    @Index(name = "idx_date_range", columnList = "event_date, event_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 事件日期
     */
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /**
     * 事件类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private EventType eventType;

    /**
     * 事件标题
     */
    @Column(nullable = false, length = 500)
    private String title;

    /**
     * 事件描述
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 关联项目ID
     */
    @Column(name = "project_id")
    private Long projectId;

    /**
     * 是否紧急
     */
    @Column(name = "is_urgent")
    private Boolean isUrgent;

    /**
     * 创建时间
     */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isUrgent == null) {
            isUrgent = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
