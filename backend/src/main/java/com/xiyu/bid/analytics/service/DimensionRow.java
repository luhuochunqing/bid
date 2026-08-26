// Input: JPQL 构造器表达式投影（维度分类标签、项目 ID、项目状态、可选竞品中标状态）
// Output: 不可变维度查询单行 record
// Pos: DTO/查询行数据载体（analytics 趋势分析专用）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.entity.Project;

/**
 * 维度查询单行数据：维度分类标签 + 项目 ID + 项目状态 + 竞品中标状态（仅竞品维度有值）。
 * 由 {@link TrendAnalysisDimensionQueryService} 查询返回，
 * 由 {@link TrendAnalysisComputationService} 聚合计算投标数/中标数/中标率。
 *
 * <p>{@code competitorWonStatus} 为结果确认「竞争对手情况」表格「是否中标」列的存储值
 * （{@code project_result_competitor.notes}，枚举 已中标/未中标，历史行可为自由文本或 null），
 * 仅竞品维度查询带出；其余维度恒为 null。竞品维度中标判定必须经
 * {@link CompetitorWonStatusSupport#parseCompetitorWon} 解析，
 * 禁止用 {@link #status}（我司项目级状态）推导竞品中标事实。</p>
 */
public record DimensionRow(
        String category,
        Long projectId,
        Project.Status status,
        String competitorWonStatus
) {

    /**
     * 三参便捷构造器：非竞品维度调用点与既有测试零改动兼容（competitorWonStatus 恒 null）。
     */
    public DimensionRow(String category, Long projectId, Project.Status status) {
        this(category, projectId, status, null);
    }
}
