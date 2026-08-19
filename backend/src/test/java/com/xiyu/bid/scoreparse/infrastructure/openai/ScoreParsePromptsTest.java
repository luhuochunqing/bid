// Input: ScoreParsePrompts 四个 prompt 构建方法
// Output: 模板含字面 % 时格式化不抛 UnknownFormatConversionException（回归：占30%" 线上崩溃）
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
}
