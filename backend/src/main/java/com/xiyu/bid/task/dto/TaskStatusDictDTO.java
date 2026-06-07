// Input: TaskStatusDict 实体
// Output: 前端看板列/下拉所需的只读投影
// Pos: DTO/展示层契约
// 维护声明: 仅携带 UI 渲染与业务判断所需字段；不透出实体内部时间戳/审计列。
package com.xiyu.bid.task.dto;

/**
 * 任务状态字典 DTO。
 *
 * <p>对外序列化字段与 V101 种子一致：</p>
 * <ul>
 *   <li>{@code code} — 状态逻辑主键（如 TODO / COMPLETED）</li>
 *   <li>{@code name} — 显示名</li>
 *   <li>{@code category} — 状态大类字符串（枚举 {@code name()}），前端据此做 CHECK 语义</li>
 *   <li>{@code color} — UI 色值（十六进制或 CSS 色字面量）</li>
 *   <li>{@code sortOrder} — 看板列排序（升序）</li>
 *   <li>{@code initial} — 是否为初始状态（前端新建任务默认列）</li>
 *   <li>{@code terminal} — 是否为终态（前端判定"是否已关闭"）</li>
 * </ul>
 *
 * <p>使用 Java record 保证不可变；JSON 字段名沿用 record 组件名。
 * 由于组件名为 {@code initial}/{@code terminal}，前端字段即 {@code initial}/{@code terminal}，
 * 不再透出 JavaBean 风格的 {@code isInitial}/{@code isTerminal} 实体命名。</p>
 */
public record TaskStatusDictDTO(
        String code,
        String name,
        String category,
        String color,
        int sortOrder,
        boolean initial,
        boolean terminal
) {
}
