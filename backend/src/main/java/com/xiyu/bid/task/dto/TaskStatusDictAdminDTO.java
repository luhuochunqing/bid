// Input: TaskStatusDict 实体（含审计字段）
// Output: 管理端读模型，含创建/更新时间戳
// Pos: DTO/管理端契约
// 维护声明: 仅给管理端使用；公共只读投影请用 TaskStatusDictDTO。
package com.xiyu.bid.task.dto;

import java.time.LocalDateTime;

/**
 * 任务状态字典管理端 DTO。
 *
 * <p>相对 {@link TaskStatusDictDTO} 多出审计字段（{@code createdAt}/{@code updatedAt}）
 * 与启用标志（{@code enabled}），仅供管理后台读取使用。
 * 字段命名沿用 record 组件风格，{@code initial}/{@code terminal} 与公共 DTO 保持一致，
 * 不暴露 JavaBean 风格的 {@code isInitial}/{@code isTerminal}。</p>
 */
public record TaskStatusDictAdminDTO(
        String code,
        String name,
        String category,
        String color,
        int sortOrder,
        boolean initial,
        boolean terminal,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
