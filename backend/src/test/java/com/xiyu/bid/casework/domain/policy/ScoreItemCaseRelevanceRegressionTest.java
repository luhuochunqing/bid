package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreItemCaseRelevanceRegressionTest {
    private final ScoreItemCaseMatchPolicy policy = new ScoreItemCaseMatchPolicy();
    private static final String LEGACY_SECURITY_RESPONSE = "西域具有公安部信息系统安全等级保护备案证明证书三级证书。";
    private static final String DELIVERY_DETAIL = "配送能力评价 | 4 | （1）对投标人配送响应速度（从接到订单起开始计算，配送到货的时间）承诺进行评价，满足国铁通用物资采购平台物流规则的得2分；不满足的得0分。（2）对投标人的配送网点情况进行评价，提供哈尔滨、沈阳、北京、太原、呼和浩特、郑州、武汉、西安、济南、上海、南昌、广州、南宁、成都、昆明、兰州、乌鲁木齐、西宁中的配送网点情况，数量大于12个得2分；6-12个得1分；小于6个得0分。";

    @ParameterizedTest
    @ValueSource(strings = {"资质证书", "技术方案", "综合"})
    void sameCustomerCannotMakeAnOffTopicCertificateRecommendable(String category) {
        KnowledgeCase certificate = certificate(category);
        var result = policy.evaluate(certificate, deliveryContext());
        assertThat(result.score()).isZero();
    }

    @Test
    void batchCorpusStillExcludesSameCustomerCertificateAndRetainsOtherCustomerDelivery() {
        KnowledgeCase delivery = candidate(2L, "配送能力评价", "配送方案", "物流配送方案覆盖全国配送网点，订单接收后按约定配送时效送达。", "其他客户");
        List<KnowledgeCase> corpus = List.of(certificate("技术方案"), delivery,
                candidate(3L, "应急预案", "技术方案", "火灾应急处置及演练。", "三峡集团"),
                candidate(4L, "商品定价", "技术方案", "商品价格对标及定价机制。", "三峡集团"),
                candidate(5L, "网络安全", "技术方案", "信息安全访问控制与数据加密。", "三峡集团"));
        var results = policy.evaluateAll(corpus, deliveryContext());
        assertThat(results.get(0).score()).isZero();
        assertThat(results.get(1).score()).isPositive();
    }

    @Test
    void keywordHitCannotReplaceSelectedItemContentRelevance() {
        var context = new ScoreItemCaseMatchPolicy.ScoreItemContext(45L, "45", "配送能力评价", DELIVERY_DETAIL,
                "综合", "央企", "证书", "三峡集团采购项目", "三峡集团");
        assertThat(policy.evaluate(certificate("技术方案"), context).score()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"detail", "qualification"})
    void projectTitleFallbackCannotReplaceUntitledRequirements(String source) {
        var context = new ScoreItemCaseMatchPolicy.ScoreItemContext(null, null, null,
                "detail".equals(source) ? DELIVERY_DETAIL : null,
                "综合", "央企", null, "三峡集团历史采购项目", "三峡集团",
                "qualification".equals(source) ? DELIVERY_DETAIL : null, List.of());
        assertThat(policy.evaluate(certificate("技术方案"), context).score()).isZero();
    }

    @Test
    void capabilityLabelAndScoreAwardBoilerplateCannotMakeCertificateRelevant() {
        KnowledgeCase certificate = legacySecurityCase(LEGACY_SECURITY_RESPONSE);
        String text = ScoreItemCaseTextMatchPolicy.candidateFullText(certificate);
        assertThat(ScoreItemCaseTextMatchPolicy.calculateTitleSimilarity(deliveryContext(), certificate, text)).isZero();
        assertThat(ScoreItemCaseTextMatchPolicy.calculateDetailOverlap(DELIVERY_DETAIL, text,
                ScoreItemCaseTextMatchPolicy.Corpus.EMPTY)).isZero();
        assertThat(policy.evaluate(certificate, deliveryContext()).score()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            LEGACY_SECURITY_RESPONSE,
            "西域已取得公安部信息系统安全等级保护备案证明第4级证书。",
            "我方持有公安部信息系统安全等级保护备案证明三级证书。",
            "具有公安部信息系统安全等级保护备案证明证书三级证书。"
    })
    void legacySecurityCertificateMatchesItsOwnScoreItemButNotDelivery(String response) {
        KnowledgeCase certificate = legacySecurityCase(response);

        assertThat(policy.evaluate(certificate, securityCertificateContext()).score()).isPositive();
        assertThat(policy.evaluate(certificate, deliveryContext()).score()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "西域具有公安部信息系统安全等级保护备案证明证书二级证书。",
            "西域不具有公安部信息系统安全等级保护备案证明证书三级证书。",
            "西域承诺具有公安部信息系统安全等级保护备案证明证书三级证书。",
            "其合作方具有公安部信息系统安全等级保护备案证明证书三级证书。",
            "西域的合作方已取得公安部信息系统安全等级保护备案证明证书三级证书。",
            "招标要求具有公安部信息系统安全等级保护备案证明证书三级证书。",
            "评审标准：具有公安部信息系统安全等级保护备案证明证书三级证书。",
            "本方案提供网站安全加固及等保备案咨询。"
    })
    void technicalCaseCannotUseTenderWordingOrAnInsufficientResponseAsCertificate(String response) {
        KnowledgeCase candidate = legacySecurityCase(response);

        var result = policy.evaluate(candidate, securityCertificateContext());
        assertThat(result.score()).isZero();
        assertThat(result.matchReasons()).containsExactly("大类冲突一票否决");
    }

    @Test
    void deliveryOriginalWordingIsRecognizedWithoutDependingOnCandidateLabels() {
        assertThat(CaseSubTopicPolicy.inferSubTopic("45 配送能力评价", DELIVERY_DETAIL))
                .isEqualTo(CaseSubTopicPolicy.SubTopic.LOGISTICS_WAREHOUSE);
        assertThat(CaseSubTopicPolicy.inferSubTopic("技术部分", DELIVERY_DETAIL))
                .isEqualTo(CaseSubTopicPolicy.SubTopic.LOGISTICS_WAREHOUSE);
    }

    @Test
    void customerBoostStillRanksContentRelevantCases() {
        KnowledgeCase same = candidate(2L, "配送能力评价", "配送方案", "物流配送覆盖全国配送网点，并承诺配送时效。", "三峡集团");
        KnowledgeCase other = candidate(3L, "配送能力评价", "配送方案", "物流配送覆盖全国配送网点，并承诺配送时效。", "其他客户");
        assertThat(policy.evaluate(same, deliveryContext()).score())
                .isGreaterThan(policy.evaluate(other, deliveryContext()).score());
    }

    @Test
    void explicitCertificateRequirementsAndProjectDefaultsRemainAvailable() {
        KnowledgeCase certificate = certificate("资质证书");
        var requirement = new ScoreItemCaseMatchPolicy.ScoreItemContext(1L, "1", "资质证书", "提供网络安全等级保护三级备案证明证书。", "综合", "央企", null);
        assertThat(policy.evaluate(certificate, requirement).score()).isPositive();
        var defaults = new ScoreItemCaseMatchPolicy.ScoreItemContext(null, null, null, null, "综合", "央企", null, "三峡集团采购项目", "三峡集团");
        assertThat(policy.evaluate(certificate, defaults).score()).isPositive();
    }

    private static ScoreItemCaseMatchPolicy.ScoreItemContext deliveryContext() {
        return new ScoreItemCaseMatchPolicy.ScoreItemContext(45L, "45", "45 配送能力评价", DELIVERY_DETAIL,
                "综合", "央企", null, "三峡集团采购项目", "三峡集团");
    }

    private static ScoreItemCaseMatchPolicy.ScoreItemContext securityCertificateContext() {
        return new ScoreItemCaseMatchPolicy.ScoreItemContext(null, "43", "网站安全等级防护能力",
                "具有公安部信息系统安全等级保护备案证明证书三级及以上证书得 1 分",
                "综合", "央企", null, "三峡集团采购项目", "三峡集团");
    }

    private static KnowledgeCase legacySecurityCase(String response) {
        KnowledgeCase certificate = candidate(331L, "网站安全等级防护能力", "网站安全等级防护能力",
                response, "三峡集团历史采购项目");
        certificate.setRequirementRaw("具有公安部信息系统安全等级保护备案证明证书三级及以上证书得1分，二级及以下得0分。");
        certificate.setBidResult("LOST");
        certificate.setQualityScore(60);
        return certificate;
    }

    private static KnowledgeCase certificate(String category) {
        KnowledgeCase certificate = candidate(1L, "网站安全等级防护证书", category,
                "西域具有公安部信息系统安全等级保护备案证明三级证书。", "三峡集团历史采购项目");
        certificate.setIsPinned(true);
        certificate.setReuseCount(1000);
        return certificate;
    }

    private static KnowledgeCase candidate(Long id, String title, String category, String response, String project) {
        KnowledgeCase candidate = new KnowledgeCase();
        candidate.setId(id);
        candidate.setScoringPointTitle(title);
        candidate.setScoringCategory(category);
        candidate.setResponseText(response);
        candidate.setSourceProjectName(project);
        candidate.setProjectType("综合");
        candidate.setCustomerType("央企");
        candidate.setBidResult("WON");
        candidate.setQualityScore(90);
        return candidate;
    }
}
