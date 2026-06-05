// Input: TaskExtendedField 实体（含审计字段）
// Output: 管理端读模型，含创建/更新时间戳
// Pos: DTO/管理端契约
// 维护声明: 仅给管理端使用；公共读模型请使用 TaskExtendedFieldDTO（公开 reader）。
package com.xiyu.bid.task.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务扩展字段管理端 DTO。
 *
 * <p>相对公共读模型多出审计字段（{@code createdAt}/{@code updatedAt}）与启用标志
 * （{@code enabled}），仅供管理后台读取使用。{@code options} 为反序列化后的
 * 选项列表（仅 select 类型有值）；{@code fieldType} 以 enum 名称的字符串形式暴露
 * （text/textarea/number/date/select），与 V103 CHECK 约束一致。</p>
 */
public record TaskExtendedFieldAdminDTO(
        String key,
        String label,
        String fieldType,
        boolean required,
        String placeholder,
        List<OptionItem> options,
        Integer sortOrder,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * select 类型字段的单个下拉选项。
     *
     * <p>{@code value} 类型保留为 {@link Object}，以兼容字符串、数字等多种原始类型值。</p>
     */
    public record OptionItem(String label, Object value) {
    }
}
