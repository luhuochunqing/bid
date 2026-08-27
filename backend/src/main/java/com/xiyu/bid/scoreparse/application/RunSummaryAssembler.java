// Input: RecallOutcome(四路遥测) + 表格行数 + 归并守卫计数 + 权重终态 + 项数
// Output: Assembled(runSummaryJson, warnLowAlignment, warnHighHallucination)
// Pos: scoreparse/application — 纯函数装配器（spec 057 FR-04/FR-06，可确定性单测）
// 维护声明: 序列化复用 Jackson（设计弯路审查 P1：手写 escape 是自建正确性风险面）；
//   键序由 LinkedHashMap 固定且是测试断言的隐含契约；阈值口径见 ScoreParseConstants
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreParseConstants;
import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreGlobalMergeExecutor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * run-summary JSON 装配器：一次解析一份运行档案，失败可追溯、按模型归因。
 * <p>阈值告警为纯判定（不打日志），日志由调用方输出——保持本类可确定性单测。
 */
final class RunSummaryAssembler {

    /** 阈值口径移至 {@link ScoreParseConstants}（spec 057 FR-06 唯一出处） */
    static final double ALIGNED_RATIO_FLOOR = ScoreParseConstants.ALIGNED_RATIO_FLOOR;
    static final double HALLUCINATION_RATIO_CEIL = ScoreParseConstants.HALLUCINATION_RATIO_CEIL;

    /** Jackson 实例线程安全，静态复用；字段全为受控基础类型 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Assembled(String json, boolean warnLowAlignment, boolean warnHighHallucination) {
    }

    /** 归并统计视图：从 {@link ScoreGlobalMergeExecutor} 编排层解耦的只读投影 */
    record MergeView(boolean invoked, boolean truncated, int poolBackedKept,
            int hallucinationDropped, int restored) {
    }

    private RunSummaryAssembler() {
    }

    static Assembled assemble(RecallOutcome recall, int tableRows, MergeView merge,
            WeightSumCheck.Result weight, int itemCount, String llmModel) {
        double alignedRatio = recall.alignedTotal() > 0
                ? (double) recall.alignedHit() / recall.alignedTotal() : -1;
        boolean warnLowAlignment = alignedRatio >= 0 && alignedRatio < ALIGNED_RATIO_FLOOR;
        int guardDenominator = merge.poolBackedKept() + merge.restored() + merge.hallucinationDropped();
        boolean warnHighHallucination = merge.invoked()
                && ratioOf(merge.hallucinationDropped(), guardDenominator) > HALLUCINATION_RATIO_CEIL;

        Map<String, Object> lanes = new LinkedHashMap<>();
        for (String lane : new String[] {"lane1", "lane2", "lane3", "lane4"}) {
            lanes.put(lane, recall.laneCounts().getOrDefault(lane, 0));
        }
        Map<String, Object> aligned = new LinkedHashMap<>();
        aligned.put("hit", recall.alignedHit());
        aligned.put("total", recall.alignedTotal());
        Map<String, Object> mergeOut = new LinkedHashMap<>();
        mergeOut.put("invoked", merge.invoked());
        mergeOut.put("truncated", merge.truncated());
        mergeOut.put("poolBackedKept", merge.poolBackedKept());
        mergeOut.put("hallucinationDropped", merge.hallucinationDropped());
        mergeOut.put("restored", merge.restored());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("llmModel", llmModel == null ? "" : llmModel);
        root.put("lanes", lanes);
        root.put("tableRows", tableRows);
        root.put("skippedChunks", recall.skippedChunks());
        root.put("aligned", aligned);
        root.put("merge", mergeOut);
        root.put("items", itemCount);
        root.put("finalTotalWeight", weight.totalWeight());
        return new Assembled(writeJson(root), warnLowAlignment, warnHighHallucination);
    }

    /** 单点异常边界：当前字段全为受控基础类型，IOException 实际不可达 */
    private static String writeJson(Map<String, Object> root) {
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("run_summary 序列化失败（不可达分支）", e);
        }
    }

    private static double ratioOf(int part, int whole) {
        return whole <= 0 ? 0 : (double) part / whole;
    }
}
