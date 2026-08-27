// Input: 各召回 lane 候选清单 + 跳过 chunk 数
// Output: 召回遥测载体（spec 046 US3 / spec 057 FR-05 lane 贡献与对齐统计）
// Pos: scoreparse/domain — 召回结果值对象（无框架依赖，可被 application/infrastructure 双方引用）
// 维护声明: 维护者按项目SOP；自 infrastructure.openai 下移以解反向依赖（xcode-review B-7）；修改时保证兼容旧构造器（candidates, skippedChunks）
package com.xiyu.bid.scoreparse.domain;

import java.util.List;
import java.util.Map;

/**
 * 四路召回结果（spec 046 FR-005）：候选池 + 遥测统计。
 *
 * @param candidates    合并前候选池（召回一/二/三/四 concat）
 * @param skippedChunks 重试一次后仍失败的 chunk 数（0 表示无跳过）
 * @param laneCounts    各 lane 候选数（lane1 正则/lane2 结构/lane3 语义/lane4 LLM），金标贡献度分析数据源
 * @param alignedHit    lane4 输出 detail 为 chunk 原文精确子串的命中数（对齐回填成功代理指标）
 * @param alignedTotal  lane4 候选总数（alignedHit 分母；比率 <50% 提示模型改写严重）
 * @param llmModel      本轮实际网关模型名（null=lane4 未执行）；§7 空返回事故按模型归因的依据
 */
public record RecallOutcome(List<ScoreCandidate> candidates, int skippedChunks,
        Map<String, Integer> laneCounts, int alignedHit, int alignedTotal, String llmModel) {

    public RecallOutcome(List<ScoreCandidate> candidates, int skippedChunks) {
        this(candidates, skippedChunks, Map.of(), 0, 0, null);
    }
}
