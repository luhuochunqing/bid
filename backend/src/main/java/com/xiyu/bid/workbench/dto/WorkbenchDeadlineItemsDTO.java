package com.xiyu.bid.workbench.dto;

import java.util.List;

/**
 * 工作台截止时间模块列表数据 DTO（CO-593）。
 *
 * <p>三个模块各返回时间窗内全部条目（按日期升序），前端负责 4 条可见 + 滚动。</p>
 */
public record WorkbenchDeadlineItemsDTO(
        List<DeadlineItemDTO> registrationDeadline,
        List<DeadlineItemDTO> bidOpening,
        List<DeadlineItemDTO> depositDeadline
) {
    public static WorkbenchDeadlineItemsDTO empty() {
        return new WorkbenchDeadlineItemsDTO(List.of(), List.of(), List.of());
    }
}
