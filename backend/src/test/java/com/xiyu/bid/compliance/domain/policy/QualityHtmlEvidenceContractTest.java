// Input: 明确业务契约或已标记的历史启发式观察输入
// Output: 保留原测试断言；语料不作为默认门禁
// Pos: compliance/domain/policy - semantic boundary 技术债分层测试
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityHtmlEvidenceContractTest {

    @Test
    void headinglessHtmlRequirementIsCleanedBeforeFusionAndMatching() {
        String raw = "具有EDI和<br>ICP许可证";
        var tender = DocumentContent.fromText("tender.pdf", "hash", raw);
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);

        assertThat(requirements.certificateRequirements()).containsExactly("具有EDI和ICP许可证");
        assertThat(requirements.qualificationConditions().getFirst().source().quote()).isEqualTo(raw);
        assertThat(QualificationMatchPolicy.isQualificationMatch(
                requirements.certificateRequirements().getFirst(), "EDI和ICP许可证")).isTrue();
        var bid = DocumentContent.fromText("bid.pdf", "bid_hash",
                "EDI和ICP许可证\n证书编号：B2-20200044\n有效期限：2025年7月4日至2030年3月28日");
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, tender, requirements, List.of()))
                .hasValueSatisfying(result -> assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS));
    }

    @Test
    void headinglessEntitiesAreDecodedOnceAndKeepOriginalEvidence() {
        String raw = "投标人须具有ISO9001&amp;#49;认证";
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                DocumentContent.fromText("tender.pdf", "hash", raw));

        assertThat(requirements.certificateRequirements()).containsExactly("投标人须具有ISO9001&#49;认证");
        assertThat(requirements.qualificationConditions().getFirst().source().quote()).isEqualTo(raw);
        assertThat(QualificationMatchPolicy.isQualificationMatch(
                requirements.certificateRequirements().getFirst(), "ISO90011认证")).isFalse();
        assertThat(QualificationMatchPolicy.isQualificationMatch(
                requirements.certificateRequirements().getFirst(), "ISO9001&#49;认证")).isTrue();
    }

    @Test
    void encodedMarkupSurvivesParagraphClassification() {
        var conditions = QualificationConditionExtractor.extractStructured(DocumentContent.fromText(
                "tender.pdf", "hash", "七、投标人资格要求\n投标人须具有ISO&lt;b&gt;9001认证"));
        assertThat(conditions).hasSize(1);
        assertThat(conditions.getFirst().text()).isEqualTo("投标人须具有ISO<b>9001认证");
        assertThat(conditions.getFirst().certificates()).extracting(QualificationCondition.CertificateRequirement::requirementText)
                .containsExactly("投标人须具有ISO<b>9001认证");
    }

    @Test
    void encodedMarkupSurvivesTableAndRequirementsReconstruction() {
        var document = DocumentContent.fromText("tender.pdf", "hash",
                "七、投标人资格要求\n| 序号 | 资格要求 |\n| 1 | 投标人须具有ISO&lt;b&gt;9001认证及A&amp;lt;B认证 |");
        var conditions = QualificationConditionExtractor.extractStructured(document);
        assertThat(conditions).hasSize(1);
        assertThat(conditions.getFirst().text()).isEqualTo("投标人须具有ISO<b>9001认证及A&lt;B认证");
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), document);
        for (int i = 0; i < 3; i++) {
            requirements = requirements.withMergedQualifications(List.of("投标人须具有营业执照"));
            assertThat(requirements.certificateRequirements()).contains("投标人须具有ISO<b>9001认证及A&lt;B认证");
            assertThat(requirements.mandatoryQualifications()).doesNotContain("投标人须具有ISO9001认证及A<B认证");
        }
    }

    @Test
    void legacyRequirementsDecodeOnceBeforeReconstruction() {
        var requirements = new TenderRequirements(List.of(), List.of("投标人须具有ISO&lt;b&gt;9001认证及A&amp;lt;B认证"),
                List.of(), List.of(), List.of(), false);
        requirements = requirements.withMergedQualifications(List.of("投标人须具有营业执照"));
        assertThat(requirements.certificateRequirements()).contains("投标人须具有ISO<b>9001认证及A&lt;B认证");
    }

    @Test
    void legacyRequirementsKeepRawQuoteAcrossReconstruction() {
        String raw = "投标人须具有ISO&#57;001认证";
        var requirements = new TenderRequirements(List.of(), List.of(raw), List.of(), List.of(), List.of(), false);
        requirements = requirements.withMergedQualifications(List.of("投标人须具有营业执照"));
        var condition = requirements.qualificationConditions().stream()
                .filter(c -> c.text().equals("投标人须具有ISO9001认证")).findFirst().orElseThrow();
        assertThat(condition.source().quote()).isEqualTo(raw);
    }

    @Test
    void vetoHtmlMatchesRetainActualSubstantivePageAndRawQuotes() {
        for (String raw : List.of("EDI和<br>ICP许可证", "ISO&#57;001认证")) {
            String nameOnly = QualificationConditionExtractor.stripHtml(raw);
            String bidText = raw + "\n证书编号：合字B2-20210088\n有效期限：2025年3月15日至2030年3月14日";
            String tenderText = "投标人须具有" + raw;
            var bid = new DocumentContent("bid.pdf", "bid", nameOnly + "\n" + bidText,
                    List.of(new DocumentContent.PageContent(2, nameOnly, List.of()),
                            new DocumentContent.PageContent(8, bidText, List.of())));
            var tender = new DocumentContent("tender.pdf", "tender", tenderText,
                    List.of(new DocumentContent.PageContent(6, tenderText, List.of())));
            var requirements = new TenderRequirements(List.of(), List.of(tenderText), List.of(), List.of(), List.of(), false);
            var result = QualificationVetoPolicy.checkFirstStage(bid, tender, requirements, List.of()).orElseThrow();
            assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
            assertThat(result.evidences()).anySatisfy(e -> {
                assertThat(e.sourceType()).isEqualTo("BID");
                assertThat(e.pageNumber()).isEqualTo(8);
                assertThat(e.quote()).contains(raw, "证书编号", "有效期限");
                assertThat(bidText).contains(e.quote());
            }).anySatisfy(e -> {
                assertThat(e.sourceType()).isEqualTo("TENDER");
                assertThat(e.pageNumber()).isEqualTo(6);
                assertThat(e.quote()).contains(raw);
                assertThat(tenderText).contains(e.quote());
            });
        }
    }

    @Test
    void tableEvidenceLocatesCleanedBrAndNumericEntities() {
        for (String raw : List.of("投标人须具有EDI和<br>ICP许可证", "投标人须具有ISO&#57;001认证")) {
            var conditions = QualificationConditionExtractor.extractStructured(DocumentContent.fromText(
                    "tender.pdf", "hash", "七、投标人资格要求\n| 序号 | 资格要求 |\n| 1 | " + raw + " |"));
            var condition = conditions.getFirst();
            assertThat(condition.certificates()).hasSize(1);
            assertThat(condition.certificates().getFirst().cellIndex()).isEqualTo(2);
            assertThat(condition.source().quote()).contains(raw);
        }
    }

    @Test
    void htmlCleaningPreservesQualificationComparisons() {
        String condition = "注册资本<100万元不得投标，营业年限>3年";
        assertThat(QualificationConditionExtractor.stripHtml(condition)).isEqualTo(condition);
        assertThat(QualificationConditionExtractor.extract("七、投标人资格要求\n1. " + condition))
                .containsExactly(condition);
    }

    @Test
    void htmlEntitiesPreserveCertificateIdentity() {
        String condition = "投标人须具有ISO&#57;001认证";
        assertThat(QualificationConditionExtractor.stripHtml(condition)).isEqualTo("投标人须具有ISO9001认证");
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                DocumentContent.fromText("tender.pdf", "hash", "七、投标人资格要求\n1. " + condition));
        assertThat(requirements.certificateRequirements()).containsExactly("投标人须具有ISO9001认证");
        String decodedReq = requirements.certificateRequirements().getFirst();
        assertThat(QualificationMatchPolicy.isQualificationMatch(decodedReq, "ISO9001认证")).isTrue();
        assertThat(QualificationMatchPolicy.isQualificationMatch(decodedReq, "ISO14001认证")).isFalse();
        assertThat(QualificationMatchPolicy.isRawQualificationMatch(condition, "ISO9001认证")).isTrue();
        assertThat(QualificationMatchPolicy.isRawQualificationMatch(condition, "ISO14001认证")).isFalse();
    }

    @Test
    void structuredRequirementsDoNotSufferDoubleEntityDecodingInMatching() {
        // 验证结构化条件在知识库比对路径上不发生二次解码（如 &amp;#49; -> &#49; 不再被错误吞成 1）
        String rawTender = "七、投标人资格要求\n1. 投标人须具有ISO9001&amp;#49;认证";
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                DocumentContent.fromText("tender.pdf", "hash", rawTender));
        String decodedReq = requirements.certificateRequirements().getFirst();
        assertThat(decodedReq).isEqualTo("投标人须具有ISO9001&#49;认证");
        // 结构化匹配入口不进行二次解码，故与不同标准的 ISO90011 证书不匹配
        assertThat(QualificationMatchPolicy.isQualificationMatch(decodedReq, "ISO90011认证")).isFalse();
        assertThat(QualificationMatchPolicy.isQualificationMatch(decodedReq, "ISO9001&#49;认证")).isTrue();
    }

    @Test
    void htmlCleaningDecodesEntitiesWithoutConsumingComparisons() {
        assertThat(QualificationConditionExtractor.stripHtml(
                "<span title=\"a>b\">注册资本&lt;100万元</span>，营业年限&gt;3年，ISO&#x39;001&amp;认证&nbsp;&quot;甲&quot;&apos;乙&apos;"))
                .isEqualTo("注册资本<100万元，营业年限>3年，ISO9001&认证\u00a0\"甲\"'乙'");
        assertThat(QualificationConditionExtractor.stripHtml("EDI和<BR />ICP；未知&unknown;"))
                .isEqualTo("EDI和ICP；未知&unknown;");
    }


}
