package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreItemConstraintExtractor.EstimateCount;
import com.xiyu.bid.scoreparse.dto.BrandMatchedItem;
import com.xiyu.bid.scoreparse.dto.PersonMatchedItem;
import com.xiyu.bid.scoreparse.dto.ProjectMatchedItem;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchedItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/** Formats existing estimate fields without turning candidate facts into formal scoring proofs. */
final class EstimatedScoreBasisFormatter {
    String simple(String category, List<?> rows, EstimateCount target, BigDecimal score, BigDecimal weight) {
        String source = switch (category) {
            case "WAREHOUSE" -> "仓库"; case "PROJECT" -> "业绩";
            case "BRAND" -> "品牌授权"; case "PERSON" -> "人员"; default -> "知识库";
        };
        var ratio = BigDecimal.valueOf(Math.min(rows.size(), target.count()) * 100L)
                .divide(BigDecimal.valueOf(target.count()), 2, RoundingMode.HALF_UP);
        String records = rows.isEmpty() ? "无" : rows.stream().limit(10).map(this::recordLabel).collect(Collectors.joining("、"));
        return "知识库粗估（" + source + "）：要求" + target.count() + "项，命中" + rows.size() + "项，满足比例" + ratio
                + "%，预计" + score + "/" + weight + "；" + (target.explicit() ? "按明确要求数量" : "未指定明确数量，按有无粗估")
                + "；资料来源：" + source + "库；命中资料：" + records + (rows.size() > 10 ? "等（仅列前10条）" : "")
                + "；未核验正式评审附加条件（排名、材料组合、地区覆盖或承诺），最终评审另算";
    }

    private String recordLabel(Object row) {
        return switch (row) {
            case WarehouseMatchedItem r -> "#" + r.id() + " " + r.name();
            case ProjectMatchedItem r -> "#" + r.id() + " " + r.contractName();
            case BrandMatchedItem r -> "#" + r.id() + " " + r.brandName();
            case PersonMatchedItem r -> "#" + r.id() + " " + r.name();
            default -> "资料记录";
        };
    }
}
