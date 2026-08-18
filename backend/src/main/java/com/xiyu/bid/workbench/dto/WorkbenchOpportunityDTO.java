// Input: 已中标/流标项目的预测结果
// Output: WorkbenchOpportunityDTO - 工作台商机预测表格行
// Pos: workbench/dto/ - 工作台 DTO（不可变 record）
package com.xiyu.bid.workbench.dto;

import java.math.BigDecimal;

/**
 * 工作台商机预测行 DTO（AI 商机预测表格）。
 *
 * <p>日期统一为 {@code yyyy-MM-dd} 字符串，与 {@link DeadlineItemDTO} 约定一致。
 *
 * <ul>
 *   <li>{@code resultType} — 项目结果：{@code WON}（已中标）/ {@code FAILED}（流标）</li>
 *   <li>{@code customer} — 招标主体（项目客户，缺失时回退业主单位）</li>
 *   <li>{@code customerType} / {@code projectType} — 枚举码，由前端翻译为中文标签</li>
 *   <li>{@code near} — 预测招标时间是否在 3 个月内（前端红点高亮）</li>
 * </ul>
 */
public record WorkbenchOpportunityDTO(
        Long projectId,
        String projectName,
        String resultType,
        String customer,
        String projectLeaderName,
        String customerType,
        String projectType,
        String bidOpenTime,
        BigDecimal servicePeriodYears,
        String servicePeriodEndDate,
        String predictedBidDate,
        boolean near
) {}
