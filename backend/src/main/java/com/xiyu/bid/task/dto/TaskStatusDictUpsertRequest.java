// Input: 管理端 POST/PUT 请求体
// Output: 校验后的 upsert 命令
// Pos: DTO/管理端入参契约
// 维护声明: PATCH 语义下，可空字段 (color/sortOrder/isInitial/isTerminal) 用包装类型，
//          null 表示"保持原值不变"。
package com.xiyu.bid.task.dto;

import com.xiyu.bid.task.entity.TaskStatusCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 任务状态字典新增/更新请求。
 *
 * <p>用于管理端 POST（新建）与 PUT（覆盖更新）。
 * {@code code} 在创建时必填且需符合大写规范；更新时由路径变量决定主键，请求体内的 {@code code}
 * 仅用于校验一致性。</p>
 *
 * <p>可空字段 ({@code color}/{@code sortOrder}/{@code isInitial}/{@code isTerminal})
 * 使用包装类型，{@code null} 表示"不修改该字段"，便于 PATCH 复用。</p>
 */
public class TaskStatusDictUpsertRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Code 必须大写字母开头，仅含大写字母/数字/下划线")
    private String code;

    @NotBlank
    @Size(max = 64)
    private String name;

    @NotNull
    private TaskStatusCategory category;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "颜色必须是 #RRGGBB 格式")
    private String color;

    private Integer sortOrder;

    private Boolean isInitial;

    private Boolean isTerminal;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TaskStatusCategory getCategory() {
        return category;
    }

    public void setCategory(TaskStatusCategory category) {
        this.category = category;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getIsInitial() {
        return isInitial;
    }

    public void setIsInitial(Boolean isInitial) {
        this.isInitial = isInitial;
    }

    public Boolean getIsTerminal() {
        return isTerminal;
    }

    public void setIsTerminal(Boolean isTerminal) {
        this.isTerminal = isTerminal;
    }
}
