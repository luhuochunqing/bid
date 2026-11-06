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

    @Test
    void classify_project1119_subjectiveSchemesWithGradedReview_remainSubjective() {
        // 项目 1119 现场用例：需要专家评审的方案性、设计性内容应为主观（即便包含认证、数量或提供字样）
        String mallIntegration = "2.2.4(3) | 技术部分评分标准(30%) | 商城系统对接能力 | 对供应商商城系统对接能力进行综合评价。"
                + "供应商应按照雅江商城定义的接口标准进行对接，依据接口标准向雅江商城提供接口服务，包括商品、商品组、订单、售后、消息等接口。"
                + "供应商需提供平台对接方案，包括但不限于自有电子商城的技术架构、功能、系统安全、对接时间与人员安排、对接成功案例（需提供相关证明材料）等内容。"
                + "评审标准按A、B、C、D分档打分。 | 5%";
        assertThat(policy.classify(mallIntegration)).isEqualTo("SUBJECTIVE");

        String goodsManagement = "商品管理能力 | 对供应商商品管理能力进行综合评价。"
                + "供应商需提供价格管控措施、商品上架管理、强制认证商品质量管理、质保期管理、保质期管理、商品补货响应时效等方案；"
                + "提供体现商品丰富度的相关材料。评审标准按A、B、C、D分档打分。 | 5%";
        assertThat(policy.classify(goodsManagement)).isEqualTo("SUBJECTIVE");

        String supplierManagement = "供应商管理能力 | 对平台供应商的供应商管理能力进行综合评价。"
                + "平台供应商需提供对其供应商的具体管理措施，包括但不限于平台供应商准入管理方案及引入时效、供应商执行管理、评价与退出管理等方案。"
                + "评审标准按A、B、C、D分档打分。 | 5%";
        assertThat(policy.classify(supplierManagement)).isEqualTo("SUBJECTIVE");

        String logisticsAndWarehouse = "物流配送及仓储能力 | 对平台供应商物流配送及仓储能力进行综合评价。"
                + "平台供应商需提供物流配送方案，至少包括自有物流或者与第三方物流公司合作情况、配送网点数量及分布情况、配送范围、发货时效、配送时效及保障措施、"
                + "物流信息同步时效以及偏远地区最后一公里物流配送方案等内容；提供自建或租赁仓库数量、仓库总面积、覆盖范围、前置仓（重点西藏区域）的位置和数量、"
                + "库存管理方案等内容。评审标准按A、B、C、D分档打分。 | 5%";
        assertThat(policy.classify(logisticsAndWarehouse)).isEqualTo("SUBJECTIVE");

        String qualitySupervision = "质量监管能力 | 对平台供应商质量监管能力进行综合评价。"
                + "平台供应商需提供质量监管方案，分别针对标包一此类重点工业品和标包二、三此类通用工业品分类施策，编制监管方案，"
                + "强化源头管控、质量检验、物流跟踪、仓储管理、到货验收等，构建“来源可查、去向可追、责任到人、风险可控”的全生命周期管控体系，"
                + "管控范围覆盖商城采购、产地质检、运输物流、前置仓入出库、现场收货、领用安装、报废处理等全过程、全环节，"
                + "实现数据全程留痕、异常自动预警、问题精准追溯评审标准按A、B、C、D分档打分。 | 5%";
        assertThat(policy.classify(qualitySupervision)).isEqualTo("SUBJECTIVE");
    }

    @Test
    void classify_generalSchemeAndDesignWithExpertReview_subjective() {
        // 通用方案性、设计性内容由专家评审打分，应为主观
        assertThat(policy.classify("投标人提供系统总体设计方案和架构设计，由专家评审打分，满分10分"))
                .isEqualTo("SUBJECTIVE");
        assertThat(policy.classify("提供应急预案与处置措施方案，评审标准分A、B、C三档打分"))
                .isEqualTo("SUBJECTIVE");
        assertThat(policy.classify("提供售后服务方案，评标委员会根据方案的可行性进行综合评价打分"))
                .isEqualTo("SUBJECTIVE");
    }

    @Test
    void classify_project1119_creditAndFinancial_objective() {
        // 项目 1119 客观项：可通过比对标书内容和招标要求验证是否满足
        String credit = "信用评价 | 2023年1月1日至今，符合资格审查条件，并且：（1）在该网站无不良记录，得100分；"
                + "（2）在该网站有处罚记录，无罚款，得90分；（3）在该网站有处罚记录，罚款金额20万元以内，得80分；"
                + "（4）在该网站有处罚记录，罚款金额50万元以内，得70分；（5）在该网站有处罚记录，罚款金额50万元以上，得60分。本项满分100分。 | 5%";
        assertThat(policy.classify(credit)).isEqualTo("OBJECTIVE");

        String financial = "财务状况 | 速动比率=速动资产/流动负债。速动比≥1，得50分；0.8≤速动比﹤1，得40分；速动比﹤0.8，得30分。"
                + "数据取值来源：①速动资产=<资产负债表>:（流动资产合计−存货）②流动负债=<资产负债表>:流动负债合计"
                + "（2）营业收现比率=经营活动现金流入/营业总收入。营业收现比率≥1，得50分；0.8≤营业收现比率﹤1，得40分；营业收现比率﹤0.8，得30分。"
                + "本项满分100分。 | 6%";
        assertThat(policy.classify(financial)).isEqualTo("OBJECTIVE");
    }
}
