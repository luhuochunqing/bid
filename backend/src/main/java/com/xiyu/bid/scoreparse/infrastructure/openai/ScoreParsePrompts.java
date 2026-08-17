// Input: 文档 chunk 文本（已 sanitize）+ 上下文参数
// Output: String prompt（召回三/四候选提取、完整性回补、阶段2打分）
// Pos: scoreparse/infrastructure/openai
package com.xiyu.bid.scoreparse.infrastructure.openai;

/** 评分标准解析 prompt 模板（纯静态，无状态）。 */
public final class ScoreParsePrompts {

    private ScoreParsePrompts() {
    }

    /**
     * 召回三/四：评分项候选提取（每 chunk 一次调用）。
     *
     * <p>召回三 = 评分规则语义（CONDITION_TO_SCORE 等模式）；
     * 召回四 = LLM 全文语义兜底。两者共用本 prompt，由模型输出 semanticPattern 区分来源。
     */
    public static String buildCandidateExtractionPrompt(String chunkText, int index, int total) {
        return """
                你是招标文件评分标准解析专家。以下是招标文件的第 %d/%d 个片段（可能包含评分表、
                评分办法章节、正文中的散落评分条款）。

                任务：提取本片段中所有"评分项"，输出 JSON。

                评分项判定特征（满足其一即提取）：
                - 明确的分值（如"10分"、"占30%%"、"得X分"）
                - 条件式给分（如"具备X证书的得Y分"）
                - 档位给分（如"优:5-4分 良:3-2分 一般:1分"）
                - 数量/指标给分（如"每提供1个案例得2分，最多6分"）

                输出字段要求：
                - code: 评分项编号（如 A1、1.2）；原文无编号则输出 null
                - dim: 所属评分维度（如 技术方案、商务部分、报价）；无法判断输出 null
                - detail: 评分条件的完整原文表述，禁止摘要、禁止改写
                - weight: 该项满分分值（数字）；无法解析为数字时输出 null
                - scoreTypeGuess: OBJECTIVE（可验证的量化条件）或 SUBJECTIVE（需人工评审的描述性要求）
                - contextNote: 前后文中的约束注记（如"注：得分不超过X分"）；无则 null
                - sourceText: 该项对应的原文片段（保持原文）
                - location: 定位描述（如"P47 评分办法表 第3行"）；无法定位则 null
                - semanticPattern: 从以下选一
                  CONDITION_TO_SCORE（条件式给分）、QUANTITY_TO_SCORE（数量/指标给分）、
                  GRADE_TO_SCORE（档位给分）、METRIC_TO_SCORE（公式/指标计算）、NONE（无法归类）

                硬性规则：
                - 本片段没有评分项时，输出 {"candidates":[]}
                - 禁止编造原文中不存在的内容
                - 禁止合并多个评分项为一项

                片段内容：
                %s
                """.formatted(index, total, chunkText);
    }

    /**
     * 完整性回补扫描（按需触发：WeightSumCheck 合计≠100 或结构召回存在未覆盖区域）。
     */
    public static String buildGapRecheckPrompt(
            String fullTextExcerpt, String knownItemsSummary, String zonesToCheck) {
        return """
                你是招标文件评分标准完整性校验专家。

                已提取的评分项清单（编号+名称+分值）：
                %s

                请重点复查以下易漏区域：%s
                （footnotes=脚注、table-remarks=表格备注、cross-page-refs=跨页引用/见某节）

                任务：找出上表中遗漏的评分项（重点是脚注中的给分条件、表格备注栏、
                "详见第X节"引用的散落条款、报价计算公式）。

                没有遗漏时输出 {"missedItems":[],"checkedZones":[复查过的区域]}。
                发现遗漏时按候选提取的字段要求输出 missedItems（与首次提取相同 schema）。

                文本节选：
                %s
                """.formatted(knownItemsSummary, zonesToCheck, fullTextExcerpt);
    }

    /**
     * 阶段 2：投标文件对标打分（客观项，每评分项一次调用）。
     * 主观项调用见 {@link #buildSubjectiveSuggestionPrompt}。
     */
    public static String buildObjectiveAssessmentPrompt(
            String scoreItemDetail, double weight, String bidDocExcerpt) {
        return """
                你是投标文件评审专家。对照评分标准，评估投标文件对本评分项的满足程度。

                评分标准（满分 %s 分）：
                %s

                投标文件相关内容节选：
                %s

                输出 JSON 字段要求：
                - actualScore: 投标文件实际可得分（0 到 %s 之间的数字）
                - matchRatio: 满足度百分比（0-100 整数）
                - evidence: 满足/部分满足的依据说明
                - quote: 投标文件中的原文引用（含章节/页码）；无引用输出 null
                - quoteMissing: 投标文件中找不到相关内容时为 true
                - missedReason: 未满足的具体原因；满足时输出 null
                - suggestion: 针对缺口的改进建议（一句话）

                硬性规则：
                - 只依据投标文件节选判断，禁止假设文件中不存在的内容
                - quoteMissing=true 时 quote 必须为 null
                """.formatted(weight, scoreItemDetail, bidDocExcerpt, weight);
    }

    /**
     * 阶段 2：主观项建议（不生成数字得分，SC-003 零泄漏）。
     */
    public static String buildSubjectiveSuggestionPrompt(String scoreItemDetail, String bidDocExcerpt) {
        return """
                你是投标文件评审专家。本评分项为主观评审项（由评标专家人工打分），
                你的任务仅是给出完善建议，禁止输出任何数字得分。

                评分标准：
                %s

                投标文件相关内容节选：
                %s

                输出 JSON 字段要求：
                - actualScore: 必须为 null
                - matchRatio: 必须为 null
                - evidence: null
                - quote: null
                - quoteMissing: false
                - missedReason: null
                - suggestion: 针对该主观项的投标文件完善建议（一到两句话，指出可补强的方向）
                """.formatted(scoreItemDetail, bidDocExcerpt);
    }
}
