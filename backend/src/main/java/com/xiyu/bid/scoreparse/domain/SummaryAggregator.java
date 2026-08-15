// Input: 评分项摘要清单（权重/类别/得分/状态）
// Output: 汇总统计（合计、计数、权重分组、weightWarning）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 041 FR-017 / FR-022 / SC-003

package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 阶段 1 汇总统计聚合器（spec 041 FR-017 / FR-022）。
 *
 * <p>规则：
 * <ul>
 *   <li>预计得分合计仅客观项（SC-003：主观项即使携带脏数据得分也丢弃）</li>
 *   <li>满足状态三档计数：OK / DANGER / 其余归 PENDING</li>
 *   <li>权重合计 ≠ 100（容差 ±0.5）→ weightWarning（不阻断，FR-022）</li>
 * </ul>
 */
public class SummaryAggregator {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.5");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String TYPE_OBJECTIVE = "OBJECTIVE";

    public Result aggregate(List<Item> items) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalEstScore = BigDecimal.ZERO;
        BigDecimal objectiveWeight = BigDecimal.ZERO;
        BigDecimal subjectiveWeight = BigDecimal.ZERO;
        int okCount = 0;
        int dangerCount = 0;
        int pendingCount = 0;

        if (items != null) {
            for (Item item : items) {
                if (item == null || item.weight() == null) {
                    continue;
                }
                totalWeight = totalWeight.add(item.weight());
                boolean objective = TYPE_OBJECTIVE.equals(item.scoreType());
                if (objective) {
                    objectiveWeight = objectiveWeight.add(item.weight());
                    if (item.estScore() != null) {
                        totalEstScore = totalEstScore.add(item.estScore());
                    }
                } else {
                    subjectiveWeight = subjectiveWeight.add(item.weight());
                }
                switch (item.statusStage1() == null ? "" : item.statusStage1()) {
                    case ScoreStatusPolicy.OK -> okCount++;
                    case ScoreStatusPolicy.DANGER -> dangerCount++;
                    default -> pendingCount++;
                }
            }
        }
        boolean weightWarning = totalWeight.subtract(HUNDRED).abs().compareTo(TOLERANCE) > 0;
        return new Result(totalWeight, totalEstScore, okCount, dangerCount, pendingCount,
                objectiveWeight, subjectiveWeight, weightWarning);
    }

    /** 聚合输入：评分项的汇总相关字段（与实体解耦，FP-Java 纯核心）。 */
    public record Item(BigDecimal weight, String scoreType, BigDecimal estScore, String statusStage1) {
    }

    /** 聚合输出：字段与 ScoreParseItemsDTO.Summary 一一对应。 */
    public record Result(
            BigDecimal totalWeight,
            BigDecimal totalEstScore,
            int okCount,
            int dangerCount,
            int pendingCount,
            BigDecimal objectiveWeight,
            BigDecimal subjectiveWeight,
            boolean weightWarning
    ) {
    }
}
