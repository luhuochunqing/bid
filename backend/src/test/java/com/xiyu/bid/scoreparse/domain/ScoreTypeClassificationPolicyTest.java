// Input: ScoreTypeClassificationPolicy（classify 方法）
// Output: 客观/主观判定行为验证（spec 041 FR-003）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreTypeClassificationPolicyTest {

    private final ScoreTypeClassificationPolicy policy = new ScoreTypeClassificationPolicy();

    @Test
    void classify_quantifiedCertification_objective() {
        assertThat(policy.classify("具备 CMMI 5 级认证证书的得 5 分")).isEqualTo("OBJECTIVE");
    }

    @Test
    void classify_quantifiedCount_objective() {
        assertThat(policy.classify("提供近三年类似项目业绩，每提供 1 个得 2 分，最多得 6 分"))
                .isEqualTo("OBJECTIVE");
    }

    @Test
    void classify_quantifiedYears_objective() {
        assertThat(policy.classify("项目经理具备 5 年以上项目管理经验得 3 分")).isEqualTo("OBJECTIVE");
    }

    @Test
    void classify_quantifiedPersonnelCert_objective() {
        assertThat(policy.classify("项目团队成员持有 PMP 证书，每人得 1 分，最高 3 分"))
                .isEqualTo("OBJECTIVE");
    }

    @Test
    void classify_descriptive_subjective() {
        assertThat(policy.classify("技术方案先进、合理、可行，视情况酌情给分")).isEqualTo("SUBJECTIVE");
    }

    @Test
    void classify_descriptivePlanQuality_subjective() {
        assertThat(policy.classify("实施方案完整性、可操作性强的得 8-10 分，一般的得 4-7 分"))
                .isEqualTo("SUBJECTIVE");
    }

    @Test
    void classify_priceType_subjective() {
        // 报价类归主观项：价格分由评标基准价公式计算，非知识库可预计
        assertThat(policy.classify("以通过符合性审查的最低有效投标报价为评标基准价得 30 分"))
                .isEqualTo("SUBJECTIVE");
    }

    @Test
    void classify_priceKeyword_subjective() {
        assertThat(policy.classify("投标报价得分按报价偏离率计算")).isEqualTo("SUBJECTIVE");
    }

    @Test
    void classify_nullOrDefaults_subjective() {
        // 无法判定时按主观处理（保守：主观项不参与预计得分，避免虚假高分）
        assertThat(policy.classify(null)).isEqualTo("SUBJECTIVE");
        assertThat(policy.classify("")).isEqualTo("SUBJECTIVE");
    }

    @Test
    void classify_rangeWithNumericCondition_objective() {
        // "XX㎡以上得X分" 属量化门槛
        assertThat(policy.classify("办公场地面积 500 ㎡以上得 2 分")).isEqualTo("OBJECTIVE");
    }

    @Test
    void classify_aiGuessPrioritized() {
        assertThat(policy.classify("方案阐述清晰", "OBJECTIVE")).isEqualTo("OBJECTIVE");
        assertThat(policy.classify("具备某种证书", "SUBJECTIVE")).isEqualTo("SUBJECTIVE");
    }

    @Test
    void classify_priceType_alwaysSubjectiveEvenIfAiGuessedObjective() {
        assertThat(policy.classify("投标报价得分按公式计算", "OBJECTIVE")).isEqualTo("SUBJECTIVE");
    }
}
