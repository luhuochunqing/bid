// Input: None
// Output: EventType枚举定义
// Pos: Entity/实体层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.calendar.entity;

/**
 * 日历事件类型枚举
 */
public enum EventType {
    /**
     * 截止日期
     */
    DEADLINE,

    /**
     * 会议
     */
    MEETING,

    /**
     * 里程碑
     */
    MILESTONE,

    /**
     * 提醒
     */
    REMINDER,

    /**
     * 提交
     */
    SUBMISSION,

    /**
     * 审核
     */
    REVIEW
}
