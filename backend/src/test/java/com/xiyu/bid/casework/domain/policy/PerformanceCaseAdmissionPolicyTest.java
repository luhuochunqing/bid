package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PerformanceCaseAdmissionPolicy - 业绩案例准入与分层降级（FP-Java 纯核心）")
class PerformanceCaseAdmissionPolicyTest {

    private static final String OFFICE_INDUSTRIAL_REQUIREMENT =
            "供应商近三年应具有大型集团级企业年度供应业绩：办公用品累计金额不低于2000万元；标准工业品累计金额不低于4500万元。";

    private static QualificationPerformanceRetriever.PerformanceRequirementResult requirement(String text) {
        return new QualificationPerformanceRetriever.PerformanceRequirementResult(
                text,
                List.of(),
                List.copyOf(PerformanceThresholdExtractor.detectCategories(text)),
                List.of(),
                PerformanceThresholdExtractor.extract(text));
    }

    @Test
    @DisplayName("人员/团队文案无业绩证据，准入拒绝（tier=0）")
    void testPersonnelCandidateRejected() {
        String personnel = "【Mock证明材料】证明王工有5年以上研发管理经验，拥有高级职称，参与过相似项目，项目团队配置合理。";

        assertThat(PerformanceCaseAdmissionPolicy.evaluateCandidate(personnel, requirement(OFFICE_INDUSTRIAL_REQUIREMENT)).tier())
                .isZero();
    }

    @Test
    @DisplayName("技术/方案文案无业绩证据，准入拒绝（tier=0）")
    void testTechnicalCandidateRejected() {
        String technical = "我司提供微服务高可用架构与容灾备份方案，支持国产化数据库与等保三级要求。";

        assertThat(PerformanceCaseAdmissionPolicy.evaluateCandidate(technical, requirement(OFFICE_INDUSTRIAL_REQUIREMENT)).tier())
                .isZero();
    }

    @Test
    @DisplayName("品类命中且金额证据达门槛：tier=1")
    void testThresholdMetCandidateAdmittedTopTier() {
        String candidate = "我司为大型集团客户提供办公用品年度供应服务，单个自然年累计发票金额2600万元。";

        PerformanceCaseAdmissionPolicy.AdmissionDecision decision =
                PerformanceCaseAdmissionPolicy.evaluateCandidate(candidate, requirement(OFFICE_INDUSTRIAL_REQUIREMENT));

        assertThat(decision.tier()).isEqualTo(1);
        assertThat(decision.reasons()).anyMatch(reason -> reason.contains("办公用品"));
    }

    @Test
    @DisplayName("金额证据明确低于门槛：一票否决（tier=0）")
    void testBelowThresholdCandidateRejected() {
        String candidate = "我司为大型集团客户提供办公用品年度供应服务，累计发票金额800万元。";

        assertThat(PerformanceCaseAdmissionPolicy.evaluateCandidate(candidate, requirement(OFFICE_INDUSTRIAL_REQUIREMENT)).tier())
                .isZero();
    }

    @Test
    @DisplayName("跨句金额证据仍参与一票否决（金额写到下一句不得绕过门槛）")
    void testCrossSentenceAmountStillVetoes() {
        String candidate = "为大型集团提供办公用品年度供应服务。累计发票金额800万元。";

        assertThat(PerformanceCaseAdmissionPolicy.evaluateCandidate(candidate, requirement(OFFICE_INDUSTRIAL_REQUIREMENT)).tier())
                .isZero();
    }

    @Test
    @DisplayName("品类命中但无金额证据：降级 tier=2 保留召回")
    void testCategoryHitWithoutAmountFallsToSecondTier() {
        String candidate = "我司长期为大型集团提供标准工业品年度供应服务，履约评价优秀。";

        assertThat(PerformanceCaseAdmissionPolicy.evaluateCandidate(candidate, requirement(OFFICE_INDUSTRIAL_REQUIREMENT)).tier())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("多品类混写：任一门槛达成即 tier=1（不被其他低额品类拖累）")
    void testAnyThresholdMetWinsOverOtherBelowThresholdCategory() {
        String candidate = "办公用品累计发票金额800万元；标准工业品累计发票金额4600万元。";

        assertThat(PerformanceCaseAdmissionPolicy.evaluateCandidate(candidate, requirement(OFFICE_INDUSTRIAL_REQUIREMENT)).tier())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("无品类门槛信息时仅要求业绩证据：tier=3；空要求/空文本安全拒绝")
    void testGenericRequirementRequiresPerformanceSignalOnly() {
        String perfSignal = "我司近三年累计供货业绩丰富，服务多家大型企业客户。";
        String personnel = "项目团队配置合理，项目经理具有高级职称。";

        assertThat(PerformanceCaseAdmissionPolicy.evaluateCandidate(perfSignal, requirement("具有类似供货业绩")).tier())
                .isEqualTo(3);
        assertThat(PerformanceCaseAdmissionPolicy.evaluateCandidate(perfSignal, null).tier()).isEqualTo(3);
        assertThat(PerformanceCaseAdmissionPolicy.evaluateCandidate(personnel, requirement("具有类似供货业绩")).tier())
                .isZero();
        assertThat(PerformanceCaseAdmissionPolicy.evaluateCandidate((String) null, requirement(OFFICE_INDUSTRIAL_REQUIREMENT)).tier())
                .isZero();
        assertThat(PerformanceCaseAdmissionPolicy.evaluateCandidate("   ", null).tier()).isZero();
    }

    @Test
    @DisplayName("跨候选分层编排：保留 tier1+tier2，人员/无证据候选剔除；全无证据时返回空列表")
    void testAdmitKeepsCategoryEvidenceTiers() {
        KnowledgeCase personnel = caseOf(1L, "项目实施经验", "证明王工有5年以上研发管理经验，拥有高级职称。");
        KnowledgeCase officeMet = caseOf(2L, "类似业绩", "为大型集团提供办公用品年度供应，累计发票金额2600万元。");
        KnowledgeCase industrialNoAmount = caseOf(3L, "供货业绩", "为大型集团提供标准工业品年度供应服务，履约良好。");

        List<KnowledgeCaseReadModel> admitted = PerformanceCaseAdmissionPolicy.admit(
                List.of(personnel, officeMet, industrialNoAmount), requirement(OFFICE_INDUSTRIAL_REQUIREMENT));

        assertThat(admitted).extracting(KnowledgeCaseReadModel::getId).containsExactlyInAnyOrder(2L, 3L);
        assertThat(PerformanceCaseAdmissionPolicy.admit(List.of(personnel), requirement(OFFICE_INDUSTRIAL_REQUIREMENT)))
                .isEmpty();
        assertThat(PerformanceCaseAdmissionPolicy.admit(List.of(), null)).isEmpty();
    }

    @Test
    @DisplayName("合同金额类证据可直接准入（信号门不得拒绝仅含“合同金额”措辞的达门槛候选）")
    void testContractAmountEvidenceAdmitted() {
        String candidate = "为某大型集团提供办公用品，合同金额2600万元，合同复印件见附件。";

        PerformanceCaseAdmissionPolicy.AdmissionDecision decision =
                PerformanceCaseAdmissionPolicy.evaluateCandidate(candidate, requirement(OFFICE_INDUSTRIAL_REQUIREMENT));

        assertThat(decision.tier()).isEqualTo(1);
    }

    @Test
    @DisplayName("仅“履约/中标”措辞的人员文案不得凭信号词放行")
    void testLooseSignalWordsDoNotAdmitPersonnelText() {
        String personnelA = "项目经理具备高级职称，负责项目履约管理，团队配置合理。";
        String personnelB = "项目负责人曾参与中标项目，人员履历丰富。";

        assertThat(PerformanceCaseAdmissionPolicy.evaluateCandidate(personnelA, requirement(OFFICE_INDUSTRIAL_REQUIREMENT)).tier())
                .isZero();
        assertThat(PerformanceCaseAdmissionPolicy.evaluateCandidate(personnelB, requirement(OFFICE_INDUSTRIAL_REQUIREMENT)).tier())
                .isZero();
    }

    private static KnowledgeCase caseOf(Long id, String title, String responseText) {
        KnowledgeCase knowledgeCase = new KnowledgeCase();
        knowledgeCase.setId(id);
        knowledgeCase.setScoringPointTitle(title);
        knowledgeCase.setResponseText(responseText);
        return knowledgeCase;
    }
}
