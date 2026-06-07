// Input: 管理端 PATCH /reorder 请求体
// Output: 状态字典批量排序命令
// Pos: DTO/管理端入参契约
// 维护声明: items 列表非空；每项 (code, sortOrder) 用于覆盖现有 sortOrder。
package com.xiyu.bid.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 任务状态字典批量排序请求。
 *
 * <p>用于管理端 PATCH /reorder：一次性提交多个 (code, sortOrder) 项以覆盖排序。
 * 服务端按列表顺序写入，调用方应预先对 sortOrder 做归一化（建议步长 10）。</p>
 */
public class TaskStatusDictReorderRequest {

    @NotEmpty
    private List<Item> items;

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    /**
     * 单个排序项。
     */
    public record Item(@NotBlank String code, int sortOrder) {
    }
}
