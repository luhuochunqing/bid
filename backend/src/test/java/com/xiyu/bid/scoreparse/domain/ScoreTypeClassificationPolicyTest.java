// Input: ScoreTypeClassificationPolicy（classify 方法）
// Output: 客观/主观判定行为验证（spec 041 FR-003）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreTypeClassificationPolicyTest {

    private final ScoreTypeClassificationPolicy policy = new ScoreTypeClassificationPolicy();

    @Test
    void classify_project1117WarehouseCount_objective() {
        assertThat(policy.classify("在项目所在地自有或租赁仓库数量达到3个得5分")).isEqualTo("OBJECTIVE");
    }

    @Test
    void classify_project1117ServiceAndEmergencyPlans_subjective() {
        assertThat(policy.classify("根据服务方案的完整性、合理性综合评审打分")).isEqualTo("SUBJECTIVE");
        assertThat(policy.classify("根据应急方案的针对性、可行性综合评审打分")).isEqualTo("SUBJECTIVE");
    }

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
    void classify_project22IncrementalSupplierPerformance_objective() {
        String detail = "供应商具有近三年内（2023年1月1日起至今，供应商具有1项大型集团型国央企电子商城办公用品服务业绩的不得分，"
                + "每增加1个得1分，最高得7分。";

        assertThat(policy.classify(detail, "SUBJECTIVE")).isEqualTo("OBJECTIVE");
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
    void classify_priceFormula_objective() {
        assertThat(policy.classify("以通过符合性审查的最低有效投标报价为评标基准价得 30 分"))
                .isEqualTo("OBJECTIVE");
    }

    @Test
    void classify_priceKeyword_objective() {
        assertThat(policy.classify("投标报价得分按报价偏离率计算")).isEqualTo("OBJECTIVE");
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
        assertThat(policy.classify("具备某种证书", "SUBJECTIVE")).isEqualTo("OBJECTIVE");
    }

    @Test
    void classify_explicitObjectiveEvidence_overridesWrongAiGuess() {
        assertThat(policy.classify("投标报价得分按公式计算", "SUBJECTIVE")).isEqualTo("OBJECTIVE");
        assertThat(policy.classify("提供有效期内的认证证书", "SUBJECTIVE")).isEqualTo("OBJECTIVE");
    }

    @Test
    void classify_horizontalComparison_subjective_evenWithNumbers() {
        assertThat(policy.classify("横向比较各供应商方案，优良得8-10分，中等得4-7分"))
                .isEqualTo("SUBJECTIVE");
    }

    @Test
    void classify_salesAmountWithGeneralTaxpayer_objective() {
        assertThat(policy.classify("投标人须为增值税一般纳税人，近三年年均销售额达到5000万元以上得10分，3000-5000万元得6分"))
                .isEqualTo("OBJECTIVE");
    }

    @Test
    void classify_salesAmountWithPriority_objective() {
        assertThat(policy.classify("近三年年均销售额达到1亿元以上者优先得满分15分，8000万元得10分"))
                .isEqualTo("OBJECTIVE");
    }

    @Test
    void classify_revenueThreshold_objective() {
        assertThat(policy.classify("提供经审计财务报告，年营业收入不低于5000万元得5分"))
                .isEqualTo("OBJECTIVE");
        assertThat(policy.classify("年营收≥1亿元得10分，5000万-1亿得5分"))
                .isEqualTo("OBJECTIVE");
    }

    @Test
    void classify_contractAmountWithQualityClient_objective() {
        assertThat(policy.classify("提供与优质客户签订的合同额在1000万元以上的业绩证明，每份得2分"))
                .isEqualTo("OBJECTIVE");
    }

    @Test
    void classify_descriptiveGrades_remainsSubjective() {
        assertThat(policy.classify("实施方案优秀得8-10分，优良得5-7分，中等得2-4分，一般得0-1分"))
                .isEqualTo("SUBJECTIVE");
    }

    @Test
    void classify_subjectivePlanWithMaxScore_remainsSubjective() {
        assertThat(policy.classify("根据投标人的售后服务方案进行打分，满分10分")).isEqualTo("SUBJECTIVE");
        assertThat(policy.classify("技术方案评审，满分20分")).isEqualTo("SUBJECTIVE");
        assertThat(policy.classify("应急预案：由专家评审打分，满分5分")).isEqualTo("SUBJECTIVE");
    }

    @Test
    void classify_productionQualificationPerformanceBaseAndIncremental_objective() {
        String detail = "供应商业绩 | 满足资格条件的业绩得基础分60分，在此基础上每增加一个满足资格条件的业绩（2023年1月1日至首次报价截止日，以合同签订时间为准）加20分，满分100分。 | 5%";
        assertThat(policy.classify(detail, "SUBJECTIVE")).isEqualTo("OBJECTIVE");
    }
}
