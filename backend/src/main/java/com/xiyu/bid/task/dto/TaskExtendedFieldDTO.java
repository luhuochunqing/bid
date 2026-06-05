package com.xiyu.bid.task.dto;

import java.util.List;

/**
 * 公开读取用的任务扩展字段 DTO。
 *
 * <p>注意：字段名为 {@code key}（非实体 {@code fieldKey}），与前端消费契约保持一致。
 * {@code options} 在 service 层由 Jackson 从 {@code optionsJson} 字符串解析为结构化列表，
 * 避免前端重复解析。{@code fieldType} 为枚举 {@code name()}（小写），供前端动态表单渲染直接使用。</p>
 */
public record TaskExtendedFieldDTO(
    String key,
    String label,
    String fieldType,
    boolean required,
    String placeholder,
    List<OptionItem> options,
    int sortOrder
) {
    public record OptionItem(String label, Object value) {}
}
