package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BidDocumentQualityEngineTest {

    @Test
    @DisplayName("25项聚合裁决引擎必须精确输出 25 项有序结果")
    void testEvaluateAll_ReturnsExactly25ItemsInOrder() {
        String content = "项目名称：采购项目\n投标人：西域科技\n2026年\n投标函\n致：招标人\n法定代表人身份证明\n统一社会信用代码：91310000775244192P\n大写：人民币壹佰万元整 小写：1000000\n工期：30日历天\n（盖章）\n联系人：张三 电话：13800000000\n目录\n第1页\n第2页\n第3页\n承诺函\n身份证\n开标一览表\n投标保证金\nCA数字证书\n退还开户银行\n资质证书\n前附表\n技术方案\n答疑澄清";
        DocumentContent doc = DocumentContent.fromText("full.pdf", "hash_full", content);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.fromText("tender.pdf", "hash_t", "招标文件内容"),
                Collections.emptyList(), TenderRequirements.empty(), Set.of());

        assertThat(results).hasSize(25);
        for (int i = 0; i < 25; i++) {
            assertThat(results.get(i).order()).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("一票否决项测试：提取的招标硬性要求 ISO27001 且标书缺失时断言 FAIL")
    void testQualificationVeto_WhenMissingRequiredCertificate_Fails() {
        String tenderText = "一票否决条款：投标人必须具备 ISO27001 信息安全认证证书。";
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "hash_t", tenderText);
        String bidContent = "项目名称：测试\n投标人：西域\n2026年\n具备 ISO9001 认证";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidContent);
        TenderRequirements requirements = new TenderRequirements(
                List.of(), List.of("ISO27001 信息安全认证证书"), List.of(), List.of(), List.of(), false);

        List<KnowledgeMatch> kbMatches = List.of(new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "7",
                "资质证书: ISO9001质量管理体系认证（等级: 甲级）", true));

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, tenderDoc, kbMatches, requirements, Set.of());

        QualityCheckItemResult vetoResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(vetoResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(vetoResult.conclusion()).contains("ISO27001");
        assertThat(vetoResult.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("实质性响应测试：标书中声明负偏离时判定 FAIL")
    void testResponsivenessReview_WhenNegativeDeviation_Fails() {
        String bidContent = "偏离表：付款方式存在负偏离，无法满足30天付款要求。";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidContent);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.fromText("tender.pdf", "hash_t", "招标要求"),
                Collections.emptyList(), TenderRequirements.empty(), Set.of());

        QualityCheckItemResult respResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(respResult.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(respResult.conclusion()).contains("实质性负偏离");
    }

    @Test
    @DisplayName("降级测试：招标要求哨兵态时比对项 MANUAL + [REFERENCE_MISSING]，独立项照常")
    void testEvaluateAll_WhenRequirementsMissing_ComparisonItemsDegrade() {
        String bidContent = "项目名称：测试\n投标函\n偏离表：无负偏离";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash_bid", bidContent);

        List<QualityCheckItemResult> results = BidDocumentQualityEngine.evaluateAll(
                doc, DocumentContent.empty(), Collections.emptyList(),
                TenderRequirements.missingSentinel(), Set.of());

        assertThat(results).hasSize(25);
        QualityCheckItemResult vetoResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(vetoResult.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(vetoResult.conclusion()).contains("[REFERENCE_MISSING]");

        // 独立项（实质性响应）不受哨兵态影响，照常裁决
        QualityCheckItemResult respResult = results.stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(respResult.status()).isNotEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(respResult.conclusion()).doesNotContain("[REFERENCE_MISSING]");
    }
}
