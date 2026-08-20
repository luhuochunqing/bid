// Input: ScoreOriginalTextAligner.align（原文 + LLM 输出）
// Output: 空白弹性对齐行为验证（原文保真回填的前提）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreOriginalTextAlignerTest {

    @Test
    void align_exactMatch_returnsOriginalFragment() {
        String original = "1.1 技术方案 投标人提供完整的实施方案描述，内容覆盖项目全流程。 10分";
        String aligned = ScoreOriginalTextAligner.align(
                original, "投标人提供完整的实施方案描述，内容覆盖项目全流程。");
        assertThat(aligned).isEqualTo("投标人提供完整的实施方案描述，内容覆盖项目全流程。");
    }

    @Test
    void align_llmHalfWidthSpace_originalFullWidthSpace() {
        // LLM 把全角空格复述成半角空格：对齐后必须返回原文（含全角空格）
        String original = "技术方案　完整性评价 10分";
        String aligned = ScoreOriginalTextAligner.align(original, "技术方案 完整性评价");
        assertThat(aligned).isEqualTo("技术方案　完整性评价");
    }

    @Test
    void align_llmInsertedSpaceInChinese_originalNoSpace() {
        // 中文原文无空格、模型断词加空格：段间零空白宽容仍可对齐
        String original = "具备ISO9001质量管理体系认证证书的得3分";
        String aligned = ScoreOriginalTextAligner.align(original, "具备 ISO9001 质量管理体系认证证书的得 3分");
        assertThat(aligned).isEqualTo("具备ISO9001质量管理体系认证证书的得3分");
    }

    @Test
    void align_originalHasLineBreak_llmSingleLine() {
        // 跨行单元格：原文换行、LLM 输出单行 → 返回含换行的原文片段
        String original = "评审因素：技术方案\n方案完整性、先进性\n与合理性。";
        String aligned = ScoreOriginalTextAligner.align(original, "技术方案 方案完整性、先进性 与合理性。");
        assertThat(aligned).isEqualTo("技术方案\n方案完整性、先进性\n与合理性。");
    }

    @Test
    void align_llmParaphrased_returnsNull() {
        // 模型概括改写（文字不同）→ 子串关系不成立 → null（调用方降级保留 LLM 输出）
        String original = "投标人须提供近三年类似项目业绩合同复印件，每个得2分，最多得6分。";
        assertThat(ScoreOriginalTextAligner.align(original, "提供类似项目业绩，每个2分，上限6分"))
                .isNull();
    }

    @Test
    void align_blankOrNullInputs_returnsNull() {
        assertThat(ScoreOriginalTextAligner.align(null, "任意")).isNull();
        assertThat(ScoreOriginalTextAligner.align("原文", null)).isNull();
        assertThat(ScoreOriginalTextAligner.align("原文", "   ")).isNull();
        assertThat(ScoreOriginalTextAligner.align("", "任意")).isNull();
    }
}
