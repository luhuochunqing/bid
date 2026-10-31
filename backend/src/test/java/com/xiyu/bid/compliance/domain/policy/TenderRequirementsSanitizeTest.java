// Input: 带引导标点或重复的资质条目
// Output: TenderRequirements 构造时清洗并去重
// Pos: compliance/domain/policy/ - 资质清洗回归（PR !2561 瘦身误删 cleanItem/去重）
package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenderRequirementsSanitizeTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"1. 成立满3年\n2. 投标人须具备ISO9001认证", "3.1.1 成立满3年\n3.1.2 投标人须具备ISO9001认证",
            "3.1.1成立满3年\n3.1.2投标人须具备ISO9001认证", "03.1.1成立满3年\n03.1.2投标人须具备ISO9001认证"})
    void numberedConditionsAreNotMistakenForSiblingSections(String conditions) {
        var text = "3.1 投标人资格要求\n" + conditions + "\n3.2 服务期限\n交付时间为30日";
        assertThat(QualificationConditionExtractor.extract(text)).containsExactly("成立满3年", "投标人须具备ISO9001认证");
    }

    @Test
    void singleLevelSiblingSectionsCannotTurnScoringCertificateIntoQualification() {
        var text = "3. 投标人资格要求\n（1）具有独立法人资格\n4. 服务期限\n服务为三年\n5. 评分标准\n\n投标人须具备ISO9001认证可得10分";
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                com.xiyu.bid.compliance.domain.DocumentContent.fromText("t.pdf", "h", text));
        assertThat(req.mandatoryQualifications()).containsExactly("具有独立法人资格");
        assertThat(req.certificateRequirements()).isEmpty();
    }

    @Test
    void ambiguousListAndChapterNumberingRetainsConditionsWithUnknownCertificateScope() {
        var text = "1. 投标人资格要求\n1. 成立满3年\n2. 投标人须具备ISO9001认证\n3. 服务期限\n服务为三年";
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                com.xiyu.bid.compliance.domain.DocumentContent.fromText("t.pdf", "h", text));
        assertThat(req.mandatoryQualifications()).anyMatch(c -> c.contains("成立满3年"));
        assertThat(req.certificateRequirements()).anyMatch(c -> c.contains("ISO9001"));
        assertThat(req.hasUnresolvedCertificateRequirements()).isTrue();
    }

    @Test
    void dateAtStartOfConditionIsNeitherChapterNorDisposableBullet() {
        var text = "3.1 投标人资格要求\n2025.01.01起成立的企业须提供营业执照\n3.2 服务期限";
        assertThat(QualificationConditionExtractor.extract(text)).containsExactly("2025.01.01起成立的企业须提供营业执照");
    }

    @Test
    void chineseListWithoutReliableChapterRolesKeepsTextAndUnknownScope() {
        var req = fused("投标人资格要求\n一、成立满3年\n二、投标人须具备ISO9001认证");
        assertThat(req.mandatoryQualifications()).containsExactly("成立满3年", "投标人须具备ISO9001认证");
        assertThat(req.hasUnresolvedCertificateRequirements()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"1.5亿元以上", "3.2 亿元以上"})
    void numericAmountIsNotAChapterOrDisposableIndex(String amount) {
        var req = fused("3.1 投标人资格要求\n" + amount + "的营业收入。\n投标人须具有ISO9001认证\n3.2 服务期限");
        assertThat(req.mandatoryQualifications()).containsExactly(amount + "的营业收入", "投标人须具有ISO9001认证");
        assertThat(req.hasUnresolvedCertificateRequirements()).isFalse();
    }

    @Test
    void markdownChildNumberKeepsItsOriginalEvidenceAndSeparateCondition() {
        String row = "#### 3.1.1成立满3年";
        var req = fused("### 3.1 投标人资格要求\n" + row + "\n#### 3.1.2投标人须具有ISO9001认证\n### 3.2 服务期限");
        assertThat(req.mandatoryQualifications()).containsExactly("成立满3年", "投标人须具有ISO9001认证");
        assertThat(req.qualificationConditions().get(0).source().quote()).isEqualTo(row);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"3.1成立满3年\n3.2投标人须具备ISO9001认证", "3.1.成立满3年\n3.2.投标人须具备ISO9001认证"})
    void compactChildNumbersUnderSingleLevelHeadingAreCleanedWithoutLosingText(String conditions) {
        assertThat(QualificationConditionExtractor.extract("3. 投标人资格要求\n" + conditions + "\n4. 服务期限"))
                .containsExactly("成立满3年", "投标人须具备ISO9001认证");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"3.1 投标人资格要求", "3.1.2投标人资格要求", "### 3.1 投标人资格要求",
            "3.1 投标人资格要求\n3.1.1 资质要求："})
    void numberedQualificationSectionRetainsFullConditionsAndStopsAtNextSection(String heading) {
        String text = heading + "\n（1）具有独立法人资格。\n（2）投标人须具备ISO9001认证。\n3.2 服务期限\n交付时间为30日";
        var doc = com.xiyu.bid.compliance.domain.DocumentContent.fromText("招标.pdf", "hash", text);
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), doc);
        assertThat(req.mandatoryQualifications()).containsExactly("具有独立法人资格", "投标人须具备ISO9001认证");
        assertThat(req.qualificationConditions()).allSatisfy(c -> {
            assertThat(c.source().fileHash()).isEqualTo("hash");
            assertThat(c.source().pageNumber()).isEqualTo(1);
        });
    }

    @Test
    void declaredSourcePageMustContainItsActualQuote() {
        String row = "| 1 | 专用资格要求 | 须具有ISO9001认证 |";
        var doc = new com.xiyu.bid.compliance.domain.DocumentContent("招标文件.pdf", "source-hash", row,
                List.of(new com.xiyu.bid.compliance.domain.DocumentContent.PageContent(7, "其他条款", List.of()),
                        new com.xiyu.bid.compliance.domain.DocumentContent.PageContent(8, row, List.of())));
        var condition = new QualificationCondition("专用资格要求；须具有ISO9001认证",
                new QualificationCondition.Source("招标文件.pdf", "source-hash", 7, row), null);
        var req = TenderRequirements.empty().withMergedQualificationConditions(List.of(condition));
        var evidence = EvidenceAssembler.semanticEvidences(
                com.xiyu.bid.compliance.domain.BidQualityCheckItem.QUALIFICATION_VETO,
                com.xiyu.bid.compliance.domain.DocumentContent.empty(), doc, req, List.of());
        assertThat(evidence).noneMatch(e -> Integer.valueOf(7).equals(e.pageNumber()));
    }

    @Test
    void structuredTableSourceReachesReportEvidence() {
        String text = "投标人资格要求\n| 序号 | 审查项目 | 审查标准 |\n| 1 | 专用资格要求 | 须具有ISO9001认证 |\n招标文件获取";
        var doc = new com.xiyu.bid.compliance.domain.DocumentContent("招标文件.pdf", "source-hash", text,
                List.of(new com.xiyu.bid.compliance.domain.DocumentContent.PageContent(7, text, List.of())));
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), doc);
        var evidence = EvidenceAssembler.semanticEvidences(
                com.xiyu.bid.compliance.domain.BidQualityCheckItem.QUALIFICATION_VETO,
                com.xiyu.bid.compliance.domain.DocumentContent.empty(), doc, req, List.of());
        assertThat(evidence).anySatisfy(e -> {
            assertThat(e.sourceType()).isEqualTo("TENDER");
            assertThat(e.pageNumber()).isEqualTo(7);
            assertThat(e.quote()).isEqualTo("| 1 | 专用资格要求 | 须具有ISO9001认证 |");
        });
    }

    @Test
    void previouslySupportedIndustryCertificatesRemainQueryable() {
        var req = TenderRequirements.empty().withMergedQualifications(List.of(
                "投标人须具备CMMI三级资质", "投标人须具备ITSS二级资质", "投标人须具备CCRC信息安全服务资质"));
        assertThat(req.certificateRequirements()).containsExactly(
                "投标人须具备CMMI三级资质", "投标人须具备ITSS二级资质", "投标人须具备CCRC信息安全服务资质");
    }

    @Test
    void sourceAndCellRoleSurviveFusionUpdatesMergeAndJsonRoundTrip() throws Exception {
        String text = "投标人资格要求\n| 序号 | 审查项目 | 审查标准 |\n| 1 | 专用资格要求 | 须具有ISO9001认证 |\n招标文件获取";
        var doc = new com.xiyu.bid.compliance.domain.DocumentContent("招标文件.pdf", "source-hash", text,
                List.of(new com.xiyu.bid.compliance.domain.DocumentContent.PageContent(7, text, List.of())));
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), doc)
                .withDepositRequirement(null).withFinancialRequirement(null).withMergedQualifications(List.of("独立法人"));
        req = TenderRequirements.merge(List.of(req));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        req = mapper.readValue(mapper.writeValueAsString(req), TenderRequirements.class);
        var condition = req.qualificationConditions().stream().filter(c -> c.text().contains("ISO9001")).findFirst().orElseThrow();
        assertThat(condition.source().fileName()).isEqualTo("招标文件.pdf");
        assertThat(condition.source().fileHash()).isEqualTo("source-hash");
        assertThat(condition.source().pageNumber()).isEqualTo(7);
        assertThat(condition.source().quote()).isEqualTo("| 1 | 专用资格要求 | 须具有ISO9001认证 |");
        assertThat(condition.certificates().get(0).cellIndex()).isEqualTo(3);
        assertThat(condition.certificates().get(0).applicability()).isEqualTo(QualificationCondition.Applicability.MANDATORY);
    }

    @Test
    void changeProofConditionDoesNotMakeMandatoryCertificateOptional() {
        var req = TenderRequirements.empty().withMergedQualifications(
                List.of("须具有有效ISO9001认证，如有变更须提供变更证明"));
        assertThat(req.certificateRequirements()).anyMatch(s -> s.contains("ISO9001"));
    }

    @Test
    void qualificationRoleInDataCellIsNotTableHeader() {
        var req = fused("投标人资格要求\n| 序号 | 审查项目 | 审查标准 |\n| 1 | 专用资格要求 | 须具有ISO9001认证 |\n招标文件获取");
        assertThat(req.mandatoryQualifications()).anyMatch(s -> s.contains("须具有ISO9001认证"));
        assertThat(req.certificateRequirements()).anyMatch(s -> s.contains("ISO9001"));
    }

    @Test
    void optionalCertificateRemainsConditionalAndDoesNotBecomeMandatoryCertificate() {
        var req = TenderRequirements.empty().withMergedQualifications(List.of("ISO9001认证（如有则提供）"));
        assertThat(req.mandatoryQualifications()).containsExactly("ISO9001认证（如有则提供）");
        assertThat(req.certificateRequirements()).isEmpty();
    }

    @Test
    void parsedGeneralConditionsReachPromptAndCertificateConsumersStaySeparate() {
        var req = com.xiyu.bid.compliance.service.TenderRequirementsParser.parse("""
                {"mandatoryQualifications":["独立法人","2023年1月1日以来类似项目业绩","ISO9001认证"],
                "compositionRequirements":["附件清单"]}
                """);
        req = TenderRequirements.merge(List.of(req)).withMergedQualifications(List.of("依法缴纳税收"));
        var prompt = LlmAdjudicator.buildPrompt(
                com.xiyu.bid.compliance.domain.BidQualityCheckItem.QUALIFICATION_VETO,
                com.xiyu.bid.compliance.domain.DocumentContent.empty(),
                com.xiyu.bid.compliance.domain.DocumentContent.empty(), req, List.of());
        assertThat(prompt).contains("独立法人", "2023年1月1日以来类似项目业绩", "依法缴纳税收");
        assertThat(req.certificateRequirements()).containsExactly("ISO9001认证");
    }

    @Test
    void markdownQualificationSectionRetainsGeneralConditions() {
        var req = fused("# 投标人资格要求\n1. 具有独立法人资格。\n2. 具备ISO9001认证。\n# 招标文件获取");
        assertThat(req.mandatoryQualifications()).anyMatch(s -> s.contains("独立法人"));
    }

    @Test
    void fileFormatAndOptionalAttachmentsDoNotBecomeMandatoryCertificates() {
        var req = fused("投标人资格要求\n1. 具有独立法人资格。\n四、投标文件格式\n1. ISO9001认证证书（如有则提供）。");
        assertThat(req.mandatoryQualifications()).noneMatch(s -> s.contains("ISO9001"));
        assertThat(req.certificateRequirements()).isEmpty();
    }

    @Test
    void actualCertificateAfterIntroductoryWordsRemainsInCertificateSubset() {
        var req = fused("投标人资格要求\n1. 具备以下证书：ISO9001认证。\n招标文件获取");
        assertThat(req.certificateRequirements()).anyMatch(s -> s.contains("ISO9001"));
    }

    private static TenderRequirements fused(String text) {
        return DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                com.xiyu.bid.compliance.domain.DocumentContent.fromText("tender.pdf", "hash", text));
    }

    @Test
    void sameLevelProcurementHeadingEndsQualificationSection() {
        var req = fused("投标人资格要求\n1. 具有独立法人资格。\n四、采购需求\n1. 供货产品应具有ISO9001认证。");
        assertThat(req.mandatoryQualifications()).containsExactly("具有独立法人资格");
    }

    @Test
    void qualificationTableCellsBecomeConditionsWithoutHeaderOrRowNumber() {
        var req = fused("投标人资格要求\n| 序号 | 资格条件 |\n|---|---|\n| 1 | 具备ISO9001认证 |\n招标文件获取");
        assertThat(req.mandatoryQualifications()).containsExactly("具备ISO9001认证");
        assertThat(req.certificateRequirements()).containsExactly("具备ISO9001认证");
    }

    @Test
    void renderedLineBreakDoesNotSplitOneQualificationCondition() {
        var req = fused("投标人资格要求：\n具有ISO9001质量管理体系\n认证证书。\n招标文件获取");
        assertThat(req.mandatoryQualifications()).containsExactly("具有ISO9001质量管理体系认证证书");
        assertThat(req.certificateRequirements()).containsExactly("具有ISO9001质量管理体系认证证书");
    }

    @Test
    void preservesLeadingYearInQualificationCondition() {
        var req = TenderRequirements.empty().withMergedQualifications(List.of("2023年1月1日以来具有类似项目业绩"));
        assertThat(req.mandatoryQualifications()).containsExactly("2023年1月1日以来具有类似项目业绩");
    }

    @Test
    void retainsNonCertificateConditionsThroughMergeAndUpdates() {
        TenderRequirements req = new TenderRequirements(List.of(),
                List.of("独立法人", "依法缴纳税收和社会保障资金", "近三年类似项目业绩", "ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        TenderRequirements merged = TenderRequirements.merge(List.of(req))
                .withMergedQualifications(List.of("具有健全的财务会计制度"));
        assertThat(merged.mandatoryQualifications()).contains(
                "独立法人", "依法缴纳税收和社会保障资金", "近三年类似项目业绩", "具有健全的财务会计制度", "ISO9001认证");
        assertThat(merged.certificateRequirements()).containsExactly("ISO9001认证");
    }

    @Test
    void deterministicFusionKeepsGeneralAndUnclassifiedQualificationConditions() {
        var tender = com.xiyu.bid.compliance.domain.DocumentContent.fromText("tender.pdf", "hash", """
                投标人资格要求
                1. 具有独立法人资格。
                2. 近三年具有类似项目业绩。
                3. 应配备不少于五名服务人员。
                4. 具备ISO9001认证。
                招标文件获取
                """);
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
        assertThat(req.mandatoryQualifications()).anyMatch(s -> s.contains("独立法人"));
        assertThat(req.mandatoryQualifications()).anyMatch(s -> s.contains("类似项目业绩"));
        assertThat(req.mandatoryQualifications()).anyMatch(s -> s.contains("五名服务人员"));
    }

    @Test
    @DisplayName("构造时剥离资质条目前导标点，并按原文去重")
    void sanitizeQualifications_stripsLeadingPunctuationAndDeduplicates() {
        TenderRequirements req = new TenderRequirements(
                List.of(),
                List.of("；ISO9001质量管理体系认证", "ISO9001质量管理体系认证", "ISO9001质量管理体系认证"),
                List.of(), List.of(), List.of(), false);

        assertThat(req.mandatoryQualifications()).containsExactly("ISO9001质量管理体系认证");
    }
}
