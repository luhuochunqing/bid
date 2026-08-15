package com.xiyu.bid.biddraftagent.domain;

import com.xiyu.bid.biddraftagent.domain.validation.KnowledgeBaseMatchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidScoreEvaluationPolicyTest {

    private final BidScoreEvaluationPolicy policy = new BidScoreEvaluationPolicy();

    @Test
    @DisplayName("空评分标准应返回空结果")
    void shouldReturnEmptyResultWhenCriteriaIsEmpty() {
        var result = policy.evaluate(List.of(), null, "test.pdf");
        assertThat(result.items()).isEmpty();
        assertThat(result.actualTotalScore()).isEqualTo(BigDecimal.ZERO);
        assertThat(result.totalWeight()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("正确区分主观项与客观项打分")
    void shouldDifferentiateSubjectiveAndObjectiveItems() {
        List<ScoringCriterion> criteria = List.of(
                new ScoringCriterion("A1", "总体架构", "微服务架构设计与高可用方案", new BigDecimal("12"), ScoringCriteriaSubType.TECHNICAL_EVALUATION),
                new ScoringCriterion("D1", "企业资质", "ISO9001 质量管理体系认证", new BigDecimal("6"), ScoringCriteriaSubType.QUALIFICATION_THRESHOLD),
                new ScoringCriterion("D2", "企业资质", "CMMI 5 级认证", new BigDecimal("5"), ScoringCriteriaSubType.QUALIFICATION_THRESHOLD)
        );

        var result = policy.evaluate(criteria, null, "标书.pdf");

        assertThat(result.items()).hasSize(3);
        assertThat(result.totalWeight()).isEqualByComparingTo("23");
        assertThat(result.subjectiveTotalWeight()).isEqualByComparingTo("12");
        assertThat(result.objectiveTotalWeight()).isEqualByComparingTo("11");

        // A1 主观项
        var a1 = result.items().get(0);
        assertThat(a1.isSubjective()).isTrue();
        assertThat(a1.actualScore()).isNull();
        assertThat(a1.status()).isEqualTo("PENDING_EXPERT");

        // D1 客观项（完全满足）
        var d1 = result.items().get(1);
        assertThat(d1.isSubjective()).isFalse();
        assertThat(d1.actualScore()).isEqualByComparingTo("6");
        assertThat(d1.status()).isEqualTo("SATISFIED");

        // D2 客观项（CMMI 5 要求，部分满足 60%）
        var d2 = result.items().get(2);
        assertThat(d2.isSubjective()).isFalse();
        assertThat(d2.actualScore()).isEqualByComparingTo("3");
        assertThat(d2.status()).isEqualTo("PARTIALLY_SATISFIED");
        assertThat(d2.suggestion()).contains("CMMI 5");

        // 总实际得分 6 + 3 = 9
        assertThat(result.actualTotalScore()).isEqualByComparingTo("9");
    }
}
