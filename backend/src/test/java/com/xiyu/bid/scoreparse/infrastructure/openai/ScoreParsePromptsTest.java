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
}
