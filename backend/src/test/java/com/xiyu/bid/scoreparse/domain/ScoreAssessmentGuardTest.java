// Input: LLM 阶段2打分原始输出 + 权重 + 评分类别
// Output: 守卫结果断言（超区间置空/主观零泄漏/quoteMissing 置空）
// Pos: scoreparse/domain — spec 041 US4 守卫测试（contracts/llm-output-schema.md §5）
package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段 2 打分结果守卫测试（spec 041 FR-016 / SC-003）。
 * <p>不信任模型数值：超区间置 null、主观项数字强制丢弃、quoteMissing 时引用置 null。
 */
class ScoreAssessmentGuardTest {

    private final ScoreAssessmentGuard guard = new ScoreAssessmentGuard();

    private ScoreAssessmentGuard.Input.InputBuilder objectiveInput() {
        return ScoreAssessmentGuard.Input.builder()
                .actualScore(new BigDecimal("6"))
                .matchRatio(60)
                .evidence("标书已提供 CMMI 3 级证书说明，部分满足")
                .quote("第 3.2 节 P15：我方已通过 CMMI 3 级认证")
                .quoteMissing(false)
                .missedReason("CMMI 5 级认证未找到匹配证书")
                .suggestion("建议启动 CMMI 5 级评估");
    }

    @Test
    void objectiveScoreWithinRange_passesThrough() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().build(), new BigDecimal("10"), "OBJECTIVE");

        assertThat(result.actualScore()).isEqualByComparingTo("6");
        assertThat(result.matchRatio()).isEqualTo(60);
        assertThat(result.quote()).isEqualTo("第 3.2 节 P15：我方已通过 CMMI 3 级认证");
        assertThat(result.rangeInvalid()).isFalse();
        assertThat(result.subjectiveDropped()).isFalse();
    }

    @Test
    void objectiveScoreAboveWeight_nullifiedAndFlagged() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().actualScore(new BigDecimal("11")).build(),
                new BigDecimal("10"), "OBJECTIVE");

        assertThat(result.actualScore()).isNull();
        assertThat(result.rangeInvalid()).isTrue();
    }

    @Test
    void objectiveScoreNegative_nullifiedAndFlagged() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().actualScore(new BigDecimal("-1")).build(),
                new BigDecimal("10"), "OBJECTIVE");

        assertThat(result.actualScore()).isNull();
        assertThat(result.rangeInvalid()).isTrue();
    }

    @Test
    void subjectiveNumericScore_forceDropped() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().build(), new BigDecimal("10"), "SUBJECTIVE");

        assertThat(result.actualScore()).isNull();
        assertThat(result.subjectiveDropped()).isTrue();
    }

    @Test
    void subjectiveMatchRatio_alsoDropped() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().build(), new BigDecimal("10"), "SUBJECTIVE");

        assertThat(result.matchRatio()).isNull();
        assertThat(result.subjectiveDropped()).isTrue();
    }

    @Test
    void subjectiveSuggestion_preserved() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().build(), new BigDecimal("10"), "SUBJECTIVE");

        assertThat(result.suggestion()).isEqualTo("建议启动 CMMI 5 级评估");
        assertThat(result.missedReason()).isNull();
        assertThat(result.evidence()).isNull();
    }

    @Test
    void quoteMissing_nullifiesQuote() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().quote("模型违规输出的引用").quoteMissing(true).build(),
                new BigDecimal("10"), "OBJECTIVE");

        assertThat(result.quote()).isNull();
    }

    @Test
    void quoteMissingNull_treatedAsFalse_keepsQuote() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().quoteMissing(null).build(),
                new BigDecimal("10"), "OBJECTIVE");

        assertThat(result.quote()).isEqualTo("第 3.2 节 P15：我方已通过 CMMI 3 级认证");
    }

    @Test
    void objectiveNullScore_passesAsPendingSemantics() {
        ScoreAssessmentGuard.Result result = guard.guard(
                objectiveInput().actualScore(null).matchRatio(null).build(),
                new BigDecimal("10"), "OBJECTIVE");

        assertThat(result.actualScore()).isNull();
        assertThat(result.rangeInvalid()).isFalse();
        assertThat(result.subjectiveDropped()).isFalse();
    }
}
