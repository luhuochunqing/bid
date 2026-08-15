// Input: LLM JSON response for score item candidate extraction (召回三/四)
// Output: Mutable POJO – score candidates (Jackson + jsonschema-generator compatible)
// Pos: scoreparse/infrastructure/openai
// 契约见 specs/041-ai-score-parse-backend/contracts/llm-output-schema.md §1
package com.xiyu.bid.scoreparse.infrastructure.openai;

import java.util.List;

public class ScoreCandidateOutput {

    public List<Candidate> candidates;

    public static class Candidate {
        public String code;
        public String dim;
        /** 完整原文表述，禁止摘要 */
        public String detail;
        /** 权重分值；无法解析为数字时为 null（Validation 层丢弃并记日志） */
        public Double weight;
        /** SUBJECTIVE / OBJECTIVE */
        public String scoreTypeGuess;
        /** 前后文注记，如"注：得分不超过 X 分" */
        public String contextNote;
        /** 对应原文片段 */
        public String sourceText;
        /** 定位描述，如"P47 评分办法表 第3行" */
        public String location;
        /** CONDITION_TO_SCORE / QUANTITY_TO_SCORE / GRADE_TO_SCORE / METRIC_TO_SCORE / NONE */
        public String semanticPattern;
    }
}
