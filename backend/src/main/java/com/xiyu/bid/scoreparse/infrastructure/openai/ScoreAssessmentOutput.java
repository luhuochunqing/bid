// Input: LLM JSON response for stage-2 bid document assessment
// Output: Mutable POJO – actualScore/matchRatio/evidence/quote/missedReason/suggestion
// Pos: scoreparse/infrastructure/openai
// 契约见 specs/041-ai-score-parse-backend/contracts/llm-output-schema.md §4
package com.xiyu.bid.scoreparse.infrastructure.openai;

/**
 * 阶段 2 投标文件对标打分 LLM 输出（Jackson + jsonschema-generator 兼容）。
 * <p>数值不直接落库：ScoreScoringAppService 过 ScoreAssessmentGuard 守卫
 * （超区间置空 / 主观项数字丢弃 / quoteMissing 置空）。
 */
public class ScoreAssessmentOutput {

    /** 实际可得分；主观项输出 null */
    public Double actualScore;

    /** 满足度百分比 0-100 */
    public Integer matchRatio;

    /** 满足/部分满足的依据说明 */
    public String evidence;

    /** 投标文件原文引用（含章节页码）；quoteMissing=true 时必须 null */
    public String quote;

    /** 投标文件中找不到相关内容时 true */
    public Boolean quoteMissing;

    /** 未满足的具体原因；满足时 null */
    public String missedReason;

    /** 针对缺口的改进建议 */
    public String suggestion;
}
