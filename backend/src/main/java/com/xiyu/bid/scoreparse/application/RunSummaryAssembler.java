// Input: RecallOutcome(四路遥测) + 表格行数 + 归并守卫计数 + 权重终态 + 项数
// Output: Assembled(runSummaryJson, warnLowAlignment, warnHighHallucination)
// Pos: scoreparse/application — 纯函数装配器（spec 057 FR-04/FR-06，无框架依赖可单测）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import com.xiyu.bid.scoreparse.infrastructure.openai.RecallOutcome;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreGlobalMergeExecutor;

import java.util.Map;

/**
 * run-summary JSON 装配器：一次解析一份运行档案，失败可追溯、按模型归因。
 * <p>阈值告警为纯判定（不打日志），日志由调用方输出——保持本类可确定性单测。
 */
final class RunSummaryAssembler {

    /** 对齐率地板：低于此值说明模型改写/概括严重（对齐器未命中） */
    static final double ALIGNED_RATIO_FLOOR = 0.5;
    /** 幻觉丢弃率天花板：高于此值说明归并裁决质量劣化 */
    static final double HALLUCINATION_RATIO_CEIL = 0.3;

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
        StringBuilder json = new StringBuilder(256);
        json.append('{');
        kv(json, "llmModel", quote(llmModel == null ? "" : llmModel));
        json.append(",\"lanes\":{");
        String[] names = {"lane1", "lane2", "lane3", "lane4"};
        Map<String, Integer> lanes = recall.laneCounts();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) json.append(',');
            json.append('"').append(names[i]).append("\":").append(lanes.getOrDefault(names[i], 0));
        }
        json.append(",\"tableRows\":").append(tableRows).append('}');
        json.append(",\"skippedChunks\":").append(recall.skippedChunks());
        json.append(",\"aligned\":{\"hit\":").append(recall.alignedHit())
                .append(",\"total\":").append(recall.alignedTotal()).append('}');
        json.append(",\"merge\":{\"invoked\":").append(merge.invoked())
                .append(",\"truncated\":").append(merge.truncated())
                .append(",\"poolBackedKept\":").append(merge.poolBackedKept())
                .append(",\"hallucinationDropped\":").append(merge.hallucinationDropped())
                .append(",\"restored\":").append(merge.restored()).append('}');
        json.append(",\"items\":").append(itemCount);
        json.append(",\"finalTotalWeight\":")
                .append(weight.totalWeight() == null ? "null" : weight.totalWeight());
        json.append('}');
        return new Assembled(json.toString(), warnLowAlignment, warnHighHallucination);
    }

    private static double ratioOf(int part, int whole) {
        return whole <= 0 ? 0 : (double) part / whole;
    }

    private static void kv(StringBuilder json, String key, String value) {
        json.append('"').append(key).append("\":").append(value);
    }

    private static String quote(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 2);
        sb.append('"');
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
