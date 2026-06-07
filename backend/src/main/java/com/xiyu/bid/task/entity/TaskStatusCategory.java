package com.xiyu.bid.task.entity;

/**
 * 任务状态大类（全平台统一主数据）。
 *
 * <p>解耦任务状态的显示与业务判断：业务逻辑应基于 category
 * 进行分类判断（如"是否已关闭"），而不是基于 status code 字面量。</p>
 *
 * <p>枚举值必须与 V101 迁移的 CHECK 约束严格一致：
 * {@code category IN ('OPEN','IN_PROGRESS','REVIEW','CLOSED')}。</p>
 */
public enum TaskStatusCategory {
    /** 未开始/待办大类。 */
    OPEN,
    /** 进行中大类。 */
    IN_PROGRESS,
    /** 等待审核大类。 */
    REVIEW,
    /** 已关闭（终态）大类，包含"已完成"、"已取消"等。 */
    CLOSED
}
