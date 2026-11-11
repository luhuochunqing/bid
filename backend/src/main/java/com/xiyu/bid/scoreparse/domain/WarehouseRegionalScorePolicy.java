// Input: 评分细则 detail，权重 weight，在用仓库集合
// Output: 多区域配送仓分布得分、增仓得分及未决排序项评估结果
// Pos: scoreparse/domain — 纯核心，无框架依赖
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.dto.WarehouseMatchedItem;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 复合多区域仓储评分策略（支持六大区域分布基准分与超额增仓分，解构动态横向面积排序）。
 */
public final class WarehouseRegionalScorePolicy {

    public static final List<String> SIX_REGIONS = List.of("华北", "东北", "华东", "中南", "西南", "西北");

    private WarehouseRegionalScorePolicy() {}

    public record Assessment(
            BigDecimal score,
            String status,
            boolean kbHit,
            int coveredRegions,
            int totalWarehouses,
            String basis
    ) {}

    public static boolean isMultiRegionWarehouseClause(String text) {
        if (text == null) return false;
        long regionCount = SIX_REGIONS.stream().filter(text::contains).count();
        return regionCount >= 3 || text.contains("六大地区") || text.contains("六大区域");
    }

    // REUSE-OK: backend/src/main/java/com/xiyu/bid/ai/core/ProjectScorePreviewPolicy.java — 多区域仓储评分策略，与 AI 项目评分预览入参及领域完全不同
    public static Assessment evaluate(String detail, BigDecimal weight, List<WarehouseMatchedItem> warehouses) {
        if (warehouses == null || warehouses.isEmpty()) {
            return new Assessment(BigDecimal.ZERO, "DANGER", false, 0, 0,
                    "知识库未匹配到在用仓储数据");
        }
        Set<String> covered = new LinkedHashSet<>();
        for (String target : SIX_REGIONS) {
            for (WarehouseMatchedItem wh : warehouses) {
                if (wh.region() != null && wh.region().contains(target)) {
                    covered.add(target);
                    break;
                }
            }
        }
        int regionCount = covered.size();
        int baseScore = Math.max(0, 15 - (6 - regionCount) * 3);
        int extraWarehouses = Math.max(0, warehouses.size() - 6);
        int extraScore = Math.min(5, extraWarehouses);

        int totalCalculated = baseScore + extraScore;
        BigDecimal finalScore = new BigDecimal(totalCalculated);
        if (weight != null && finalScore.compareTo(weight) > 0) {
            finalScore = weight;
        }

        String status = regionCount == 6 ? "OK" : "PENDING";
        String basis = String.format("知识库在用仓库覆盖六大区域中 %d 个地区（%s），基础得分 %d 分；额外增仓 %d 处，增分 %d 分；累计确定性得分 %d 分；另 5 分仓储面积横向排序分需结合现场横向评比。",
                regionCount, String.join("、", covered), baseScore, extraWarehouses, extraScore, totalCalculated);

        return new Assessment(finalScore, status, true, regionCount, warehouses.size(), basis);
    }
}
