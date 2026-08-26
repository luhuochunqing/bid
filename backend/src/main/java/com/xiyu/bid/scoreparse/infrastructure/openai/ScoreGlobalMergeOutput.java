// Input: LLM JSON response for global merge adjudication (spec 055 全局归并)
// Output: Mutable POJO – merged items + decision summary (Jackson + jsonschema-generator compatible)
// Pos: scoreparse/infrastructure/openai
// 契约见 specs/055-score-parse-global-merge/contracts/llm-global-merge-schema.md §5
package com.xiyu.bid.scoreparse.infrastructure.openai;

import java.util.List;

public class ScoreGlobalMergeOutput {

    public List<MergedItem> items;

    public Summary summary;

    public static class MergedItem {
        public String code;
        public String dim;
        /** 评审细则逐字原文；必须能在章节原文中找到（守卫强制，防幻觉） */
        public String detail;
        /** 权重分值；无法解析为数字时为 null */
        public Double weight;
        /** OBJECTIVE / SUBJECTIVE / null */
        public String scoreTypeGuess;
        /** 对应原文片段 */
        public String sourceText;
    }

    public static class Summary {
        /** 本次合并掉的重复候选组数（LLM 自报，供摘要日志） */
        public Integer mergedGroupCount;
        /** 判定不属于评分项而丢弃的候选数 */
        public Integer discardedCandidateCount;
        /** ≤100 字决策说明 */
        public String notes;
    }
}
