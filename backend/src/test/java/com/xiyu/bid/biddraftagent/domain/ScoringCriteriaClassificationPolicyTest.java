// Input: ScoringCriteriaClassificationPolicy（classify + classifyAll 方法）
// Output: 评分标准子类型分类行为验证
// Pos: Test/biddraftagent/domain

package com.xiyu.bid.biddraftagent.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringCriteriaClassificationPolicyTest {

    private final ScoringCriteriaClassificationPolicy policy = new ScoringCriteriaClassificationPolicy();

    // ── classify() 单元测试 ───────────────────────────────────────────────

    @Test
    void classify_priceWeight() {
        assertThat(policy.classify("价格评分：满分30分"))
                .isEqualTo(ScoringCriteriaSubType.PRICE_WEIGHT);
    }

    @Test
    void classify_priceWeight_bidPrice() {
        assertThat(policy.classify("投标报价得分计算"))
                .isEqualTo(ScoringCriteriaSubType.PRICE_WEIGHT);
    }

    @Test
    void classify_technicalEvaluation() {
        assertThat(policy.classify("技术方案评分标准"))
                .isEqualTo(ScoringCriteriaSubType.TECHNICAL_EVALUATION);
    }

    @Test
    void classify_technicalEvaluation_systemDesign() {
        assertThat(policy.classify("系统设计方案完整性"))
                .isEqualTo(ScoringCriteriaSubType.TECHNICAL_EVALUATION);
    }

    @Test
    void classify_serviceEvaluation() {
        assertThat(policy.classify("售后服务方案"))
                .isEqualTo(ScoringCriteriaSubType.SERVICE_EVALUATION);
    }

    @Test
    void classify_serviceEvaluation_responseTime() {
        assertThat(policy.classify("技术支持响应时间≤2小时"))
                .isEqualTo(ScoringCriteriaSubType.SERVICE_EVALUATION);
    }

    @Test
    void classify_qualificationThreshold() {
        assertThat(policy.classify("具有电子与智能化工程专业承包一级资质"))
                .isEqualTo(ScoringCriteriaSubType.QUALIFICATION_THRESHOLD);
    }

    @Test
    void classify_qualificationThreshold_certification() {
        assertThat(policy.classify("ISO9001质量管理体系认证"))
                .isEqualTo(ScoringCriteriaSubType.QUALIFICATION_THRESHOLD);
    }

    @Test
    void classify_comprehensiveScore() {
        assertThat(policy.classify("综合评分法：总分100分"))
                .isEqualTo(ScoringCriteriaSubType.COMPREHENSIVE_SCORE);
    }

    @Test
    void classify_other_asDefault() {
        assertThat(policy.classify("投标文件装订要求：左侧胶装"))
                .isEqualTo(ScoringCriteriaSubType.OTHER);
    }

    @Test
    void classify_null_returnsOther() {
        assertThat(policy.classify(null)).isEqualTo(ScoringCriteriaSubType.OTHER);
    }

    @Test
    void classify_blank_returnsOther() {
        assertThat(policy.classify("  ")).isEqualTo(ScoringCriteriaSubType.OTHER);
    }

    // ── classifyAll() 集成测试 ────────────────────────────────────────────

    @Test
    void classifyAll_mixedCriteria() {
        List<String> criteria = List.of(
                "价格评分：满分30分",
                "技术方案完整性：30分",
                "售后服务方案：10分",
                "具有建筑工程施工总承包一级资质",
                "综合评分法"
        );

        List<ScoringCriteriaItem> items = policy.classifyAll(criteria);

        assertThat(items).hasSize(5);
        assertThat(items.get(0).subType()).isEqualTo(ScoringCriteriaSubType.PRICE_WEIGHT);
        assertThat(items.get(1).subType()).isEqualTo(ScoringCriteriaSubType.TECHNICAL_EVALUATION);
        assertThat(items.get(2).subType()).isEqualTo(ScoringCriteriaSubType.SERVICE_EVALUATION);
        assertThat(items.get(3).subType()).isEqualTo(ScoringCriteriaSubType.QUALIFICATION_THRESHOLD);
        assertThat(items.get(4).subType()).isEqualTo(ScoringCriteriaSubType.COMPREHENSIVE_SCORE);
    }

    @Test
    void classifyAll_empty_returnsEmpty() {
        assertThat(policy.classifyAll(List.of())).isEmpty();
    }

    @Test
    void classifyAll_null_returnsEmpty() {
        assertThat(policy.classifyAll(null)).isEmpty();
    }
}
