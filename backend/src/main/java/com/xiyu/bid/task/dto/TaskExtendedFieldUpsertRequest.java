// Input: 管理端 POST/PUT 请求体
// Output: 校验后的 upsert 命令
// Pos: DTO/管理端入参契约
// 维护声明: PATCH 语义下，可空字段使用包装类型，null 表示"保持原值不变"。
//          key 一旦落库不可变更，update 时路径变量决定主键，请求体内 key 仅用于兼容。
package com.xiyu.bid.task.dto;

import com.xiyu.bid.task.entity.TaskExtendedFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 任务扩展字段新增/更新请求。
 *
 * <p>用于管理端 POST（新建）与 PUT（覆盖更新）。{@code key} 在创建时必填，
 * 需符合小写命名规范；更新时由路径变量决定主键，请求体内 {@code key} 不会改变
 * 既有条目的主键值。</p>
 *
 * <p>可空字段（{@code required}/{@code placeholder}/{@code options}/{@code sortOrder}）
 * 使用包装类型或引用类型，{@code null} 表示"不修改该字段"，便于 PATCH 复用。</p>
 */
public class TaskExtendedFieldUpsertRequest {

    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "key 必须小写字母开头，仅含小写字母/数字/下划线")
    @Size(max = 64)
    private String key;

    @NotBlank
    @Size(max = 128)
    private String label;

    @NotNull
    private TaskExtendedFieldType fieldType;

    private Boolean required;

    @Size(max = 255)
    private String placeholder;

    private List<OptionItem> options;

    private Integer sortOrder;

    /**
     * select 类型字段的单个下拉选项。
     *
     * <p>{@code value} 保留为 {@link Object} 以兼容字符串、数字等。</p>
     */
    public record OptionItem(String label, Object value) {
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public TaskExtendedFieldType getFieldType() {
        return fieldType;
    }

    public void setFieldType(TaskExtendedFieldType fieldType) {
        this.fieldType = fieldType;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public List<OptionItem> getOptions() {
        return options;
    }

    public void setOptions(List<OptionItem> options) {
        this.options = options;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
