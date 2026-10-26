package com.xiyu.bid.scoreparse.domain.policy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrossChapterPerformancePolicyTest {

    private final CrossChapterPerformancePolicy policy = new CrossChapterPerformancePolicy();

    private static final String PROD_SCORE_DETAIL = "满足资格条件的业绩得基础分60分，在此基础上每增加一个满足资格条件的业绩（2023年1月1日至首次报价截止日，以合同签订时间为准）加20分，满分100分。 | 5%";

    @Test
    void referencesQualification_detectsIndicator() {
        assertThat(policy.referencesQualification("供应商业绩", PROD_SCORE_DETAIL)).isTrue();
        assertThat(policy.referencesQualification("商务部分", "企业近三年类似项目业绩")).isFalse();
    }

    @Test
    void extract_pullsAmountAndCategoryFromQualificationRequirements() {
        List<String> qualRequirements = List.of(
                "具有独立承担民事责任的能力",
                "投标人须具有2021年1月1日至今的工业品MRO采购供货业绩，单项合同金额不低于1000万元，需提供合同与发票",
                "具有健全的财务会计制度"
        );

        var constraint = policy.extract("供应商业绩", PROD_SCORE_DETAIL, qualRequirements, null);

        assertThat(constraint.referencesQualification()).isTrue();
        assertThat(constraint.hasEffectiveConstraint()).isTrue();
        assertThat(constraint.minContractAmount()).isEqualByComparingTo("10000000");
        assertThat(constraint.targetCategories()).contains("工业品", "mro");
        assertThat(constraint.evidenceRequirements()).contains("发票", "合同关键页");
    }

    @Test
    void extract_returnsEmptyConstraintWhenQualificationHasNoPerformanceClause() {
        List<String> qualRequirements = List.of(
                "具有独立承担民事责任的能力",
                "具备ISO9001质量管理体系认证证书"
        );

        var constraint = policy.extract("供应商业绩", PROD_SCORE_DETAIL, qualRequirements, null);

        assertThat(constraint.referencesQualification()).isTrue();
        assertThat(constraint.hasEffectiveConstraint()).isFalse();
        assertThat(constraint.rawClauseText()).isNull();
    }
}
