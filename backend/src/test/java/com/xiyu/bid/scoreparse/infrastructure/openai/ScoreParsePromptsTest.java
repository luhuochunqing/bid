// Input: ScoreParsePrompts 四个 prompt 构建方法
// Output: 模板含字面 % 时格式化不抛 UnknownFormatConversionException（回归：占30%" 线上崩溃）；
//         prompt 与 PriceScoringKeywords 关键参数同步性断言
// Pos: Test/scoreparse/infrastructure/openai

package com.xiyu.bid.scoreparse.infrastructure.openai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 回归测试：prompt 模板是 java.util.Formatter 格式串，模板文本块里的字面 % 必须写成 %%。
 * 历史事故（2026-08-17 线上）：模板示例文案 "占30%" 中 % 后紧跟引号，formatted() 抛
 * UnknownFormatConversionException: Conversion = '"'，导致评分解析第一阶段 100% 失败。
 */
class ScoreParsePromptsTest {

    /** 参数中含 %/引号：参数经 %s 透传，绝不参与格式解析，必须原样出现在结果里。 */
    private static final String TRICKY_TEXT = "本项目技术部分占60%\"，商务占30%”，报价占10%；符合率100%得满\"分";

    @Test
    void buildCandidateExtractionPrompt_templateLiteralPercentDoesNotThrow() {
        String prompt = ScoreParsePrompts.buildCandidateExtractionPrompt(TRICKY_TEXT, 2, 5);
        // 模板里写的 "占30%%" 渲染后是单 % + 英文引号；参数里的 "占30%”" 是中文引号，不会误匹配
        assertThat(prompt).contains("占30%\"");
        assertThat(prompt).contains(TRICKY_TEXT);
    }

    @Test
    void buildCandidateExtractionPrompt_mustContainCriticalPriceParams() {
        // PriceScoringKeywords 中心化后，若新增关键参数（如 折让率）未同步到 prompt，
        // 本测试失败提醒同步 ScoreParsePrompts
        String prompt = ScoreParsePrompts.buildCandidateExtractionPrompt("test", 1, 1);
        String[] critical = {"下浮率", "BP%", "HP%", "LP%", "L1", "L2", "RH", "RL", "基准价", "价格分计算", "进入价格", "报价人"};
        for (String token : critical) {
            assertThat(prompt)
                    .as("prompt 需同步包含关键参数 '%s'，若新增请同步更新 ScoreParsePrompts", token)
                    .contains(token);
        }
        // SCORE_EXTENSION 核心也需出现
        assertThat(prompt).contains("基准价");
        assertThat(prompt).contains("报价评分");
    }

    @Test
    void buildCandidateExtractionPrompt_procedureNoticeRulesAnchored() {
        // PR !2427 新增的 3 条防污染禁令：须知前附表程序条款 / 形式资格响应性评审条款 /
        // 总分构成汇总行。规则文字属关键行为契约，被删改时本测试变红。
        String prompt = ScoreParsePrompts.buildCandidateExtractionPrompt("test", 1, 1);
        String[] anchored = {"须知前附表中的程序性条款", "形式评审标准", "总分构成的汇总行"};
        for (String token : anchored) {
            assertThat(prompt)
                    .as("候选提取 prompt 需包含防污染禁令 '%s'，若调整措辞请同步更新本锚定", token)
                    .contains(token);
        }
    }

    @Test
    void buildGapRecheckPrompt_templateLiteralPercentDoesNotThrow() {
        assertThatCode(() -> ScoreParsePrompts.buildGapRecheckPrompt(
                        TRICKY_TEXT, "占比100%\"的区域", "footnotes"))
                .doesNotThrowAnyException();
    }

    @Test
    void buildObjectiveAssessmentPrompt_templateLiteralPercentDoesNotThrow() {
        assertThatCode(() -> ScoreParsePrompts.buildObjectiveAssessmentPrompt(
                        TRICKY_TEXT, 30.0, "案例满足率100%\"的证明"))
                .doesNotThrowAnyException();
    }

    @Test
    void buildSubjectiveSuggestionPrompt_templateLiteralPercentDoesNotThrow() {
        assertThatCode(() -> ScoreParsePrompts.buildSubjectiveSuggestionPrompt(
                        TRICKY_TEXT, "方案优:5-4分 良:3-2分 占比50%”"))
                .doesNotThrowAnyException();
    }

    @Test
    void buildObjectiveAssessmentPrompt_withImagesAppendsNeutralImageGuidance() {
        String prompt = ScoreParsePrompts.buildObjectiveAssessmentPrompt(
                TRICKY_TEXT, 30.0, "案例满足率证明", true);

        assertThat(prompt).contains("附图为投标文件中的部分图片");
        assertThat(prompt).contains("请先判断每张图是否与评分项相关");
        assertThat(prompt).contains("引用依据时说明来自图片");
    }

    @Test
    void buildObjectiveAssessmentPrompt_withoutImagesHasNoImageGuidance() {
        String prompt = ScoreParsePrompts.buildObjectiveAssessmentPrompt(
                TRICKY_TEXT, 30.0, "案例满足率证明");

        assertThat(prompt).doesNotContain("附图");
    }

    // ------------------------------------------------------------------
    // promptSafe 剥离：主观/客观 prompt 喂 Gemini 前抹掉 markdown 图片与
    // data:image base64 占位符，避免 Gemini 解码报 500（spec 048 测试环境回归）。
    // ------------------------------------------------------------------

    @Test
    void buildObjectiveAssessmentPrompt_stripsMarkdownImageFromBidExcerpt() {
        String excerpt = "案例证明 ![](data:image/png;base64,abc...==) 见下图\n" + TRICKY_TEXT;
        String prompt = ScoreParsePrompts.buildObjectiveAssessmentPrompt(
                TRICKY_TEXT, 30.0, excerpt);

        assertThat(prompt).doesNotContain("data:");
        assertThat(prompt).doesNotContain("base64");
        assertThat(prompt).doesNotContain("![");
        // 剥离后仅用空格替换，不吞掉相邻文本
        assertThat(prompt).contains(TRICKY_TEXT);
    }

    @Test
    void buildSubjectiveSuggestionPrompt_stripsBareDataUri() {
        String excerpt = "方案说明 data:image/jpeg;base64,/9j/4AAQ 的图片正文（占位符截断）";
        String prompt = ScoreParsePrompts.buildSubjectiveSuggestionPrompt(
                TRICKY_TEXT, excerpt);

        assertThat(prompt).doesNotContain("data:image");
        assertThat(prompt).doesNotContain("base64");
        assertThat(prompt).contains("方案说明");
        assertThat(prompt).contains("的图片正文");
    }

    @Test
    void buildObjectiveAssessmentPrompt_withImagesStillHasImageGuidanceAfterStrip() {
        String excerpt = "案例 ![](data:image/png;base64,xxx==) 附件";
        String prompt = ScoreParsePrompts.buildObjectiveAssessmentPrompt(
                TRICKY_TEXT, 30.0, excerpt, true);

        // 图片指引保留（真正图片走 multimodal content parts，不进 prompt 文本）
        assertThat(prompt).contains("附图为投标文件中的部分图片");
        assertThat(prompt).doesNotContain("data:");
    }

    @Test
    void buildCandidateExtractionPrompt_parallelSubItemsRuleAnchored() {
        String prompt = ScoreParsePrompts.buildCandidateExtractionPrompt("test", 1, 1);
        assertThat(prompt).contains("并列能力与方案评分项完整提取");
        assertThat(prompt).contains("运营服务能力");
        assertThat(prompt).contains("权重与评分项严格对齐");
    }

    @Test
    void buildGapRecheckPrompt_parallelSubItemsAndTotalWeightRuleAnchored() {
        String prompt = ScoreParsePrompts.buildGapRecheckPrompt("test", "known", "zones");
        assertThat(prompt).contains("大项分值与并列子项完整性复查");
        assertThat(prompt).contains("运营服务能力");
        assertThat(prompt).contains("30%");
    }

    @Test
    void buildGlobalMergePrompt_parallelSubItemsAndTotalWeightRuleAnchored() {
        String prompt = ScoreParsePrompts.buildGlobalMergePrompt("[]", "section");
        assertThat(prompt).contains("并列评价细则与分值闭环完整性");
        assertThat(prompt).contains("运营服务能力");
        assertThat(prompt).contains("100%");
    }
}
