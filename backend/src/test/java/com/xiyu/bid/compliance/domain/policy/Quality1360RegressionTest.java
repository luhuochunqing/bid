package com.xiyu.bid.compliance.domain.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

/** 脱敏输入覆盖项目1360的实际误报形态，不包含客户原件。 */
class Quality1360RegressionTest {
    private static final String SYSTEM_PRICE = "第三卷 经济标：标段1在系统报价表中报折扣率。"
            + "商务标、技术标部分不含折扣率报价；标段2不作价格评审，但也要在系统报价表中报折扣率。"
            + "折扣率=100×推送价格/原价，单位为%，请忽略系统报价单模板中的单位元。";

    @Test
    void scoringEvidenceMustStayAtActualTriggerInsteadOfEarlierIdenticalBlank() {
        var doc = pages("延保服务协议书\n购买日期_____年", "技术方案\n项目经理拟派：_____，负责实施交付。");
        assertThat(ScoringResponsePolicy.checkFirstStage(doc, TenderRequirements.empty()).orElseThrow().status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var result = ScoringResponsePolicy.checkFirstStage(doc, requiredScoringField("项目经理拟派")).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.evidences()).allSatisfy(e -> {
            assertThat(e.pageNumber()).isEqualTo(80);
            assertThat(e.quote()).contains("项目经理拟派").doesNotContain("延保服务协议");
        });
    }

    @Test
    void completedServiceWithFutureWarrantyAgreementMustNotBeHardFailure() {
        var doc = pages("其他附件", "售后服务方案\n承诺7×24小时响应并提供全国服务。\n"
                + "延保服务协议书\n甲方：供应商\n乙方：\n购买日期_____年，产品________，用于后续订单延保。");
        assertThat(ScoringResponsePolicy.checkFirstStage(doc, TenderRequirements.empty()).stream())
                .noneMatch(r -> r.status() == QualityCheckStatus.FAIL);
    }

    @Test
    void unknownBlankIsNotAProvenScoringFieldDefect() {
        var doc = pages("服务方案\n完整响应", "服务方案\n备注：_____。");
        assertThat(ScoringResponsePolicy.checkFirstStage(doc, TenderRequirements.empty()).stream())
                .noneMatch(r -> r.status() == QualityCheckStatus.FAIL);
    }

    @Test
    void catalogRecallMissDoesNotProveMissingBody() {
        var doc = pages("目录\n服务方案......80", "客服保障\n7×24小时受理并在2小时内解决，团队职责与应急响应已展开。");
        assertThat(ScoringResponsePolicy.checkFirstStage(doc, TenderRequirements.empty()).stream())
                .noneMatch(r -> r.status() == QualityCheckStatus.FAIL);
    }

    @Test
    void qualificationStatementElsewhereOnPageDoesNotOwnWarrantyBlanks() {
        var doc = pages("附件", "资格声明\n我方不存在失信记录，符合资格条件。\n"
                + "延保服务协议书\n甲方：供应商\n乙方：\n购买日期_____年，产品________。");
        assertThat(QualificationVetoPolicy.checkFirstStage(doc, DocumentContent.empty(), TenderRequirements.empty(), List.of()).stream())
                .noneMatch(r -> r.status() == QualityCheckStatus.FAIL);
    }

    @Test
    void qualificationPlaceholderEvidenceAlsoStaysAtActualTrigger() {
        var doc = pages("延保服务协议书\n日期_____年", "资格声明\n失信被执行人核查结果：_____。");
        var result = QualificationVetoPolicy.checkFirstStage(doc, DocumentContent.empty(), TenderRequirements.empty(), List.of()).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.evidences().getFirst().pageNumber()).isEqualTo(80);
    }

    @ParameterizedTest
    @ValueSource(strings = {"注：上述材料所填信息务必真实有效！否则将按弄虚作假进行处罚！",
            "承诺函\n我方承诺不存在以下情形之一：\n1. 被列入失信名单；\n2. 处于禁止投标期；\n3. 串通投标、弄虚作假。"})
    void warningAndNegatedListDoNotBecomeActualMisconduct(String text) {
        assertThat(SubItemAndAttachmentRules.checkVetoKeyword(DocumentContent.fromText("bid.docx", "hash", text)).status())
                .isNotEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void actualMisconductAfterNegatedListIsStillRejected() {
        var doc = DocumentContent.fromText("bid.pdf", "hash", "我方承诺不存在以下情形：\n1. 弄虚作假。\n"
                + "实际情况：我司已进行违法分包，由第三方实际履约。");
        assertThat(SubItemAndAttachmentRules.checkVetoKeyword(doc).status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void discountSubmissionChannelSurvivesExtractionAndFusion() {
        var det = DeterministicPriceExtractor.extract(SYSTEM_PRICE);
        var fused = PriceRequirement.fuseWithDeterministic(PriceRequirement.standard(), det);
        assertThat(fused.description()).contains("系统报价表", "折扣率");
        assertThat(fused.requireBidOpeningTable()).isTrue();
        assertThat(fused.requireItemizedList()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAIL", "PASS"})
    void systemQuoteCannotBeCertifiedOrRejectedFromWordOnly(String verdict) {
        var tender = DocumentContent.fromText("tender.pdf", "th", SYSTEM_PRICE);
        var req = TenderRequirements.empty().withPriceRequirement(DeterministicPriceExtractor.extract(SYSTEM_PRICE));
        var bid = DocumentContent.fromText("bid.docx", "bh", "经济标承诺函\n具体折扣率详见系统报价表。");
        var llm = new LlmAdjudicator(p -> LlmVerdict.success(new LlmVerdict(verdict, "", "模型判断传统报价表完整性", "")));
        var all = BidDocumentQualityEngine.evaluateAll(bid, tender, List.of(), req, Set.of(), llm, (p,s) -> { });
        assertThat(all).hasSize(25);
        assertThat(all.stream().filter(r -> r.itemCode().equals("BID_OPENING_TABLE")).findFirst().orElseThrow().status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void explicitAdditionalOpeningTableRequirementIsNotExempted() {
        var text = SYSTEM_PRICE + "\n投标文件还须另附开标一览表及分项报价明细表。";
        var req = DeterministicPriceExtractor.extract(text);
        assertThat(req.requireBidOpeningTable()).isTrue();
        assertThat(req.requireItemizedList()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"授权委托书\n身份证国徽面、人像面\n【已附图片】\n【已附图片】",
            "法定代表人身份证明\n公民身份号码：110105199001011234"})
    void imageMarkerAndNumberCannotCertifyIdentityDocumentCompleteness(String text) {
        var bid = DocumentContent.fromText("bid.docx", "bh", text);
        assertThat(IdAuthLetterCheckPolicy.check(bid).status()).isNotEqualTo(QualityCheckStatus.PASS);
        var llm = new LlmAdjudicator(p -> LlmVerdict.success(new LlmVerdict("PASS", "", "已附图片，身份证正反面齐全", "")));
        var all = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(), TenderRequirements.empty(), Set.of(), llm, (p,s) -> { });
        assertThat(all.stream().filter(r -> r.itemCode().equals("ID_AUTH_LETTER")).findFirst().orElseThrow().status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void modelOutputExampleIsValidJsonWithAllRequiredKeys() throws Exception {
        String prompt = LlmAdjudicator.buildPrompt(BidQualityCheckItem.SCORING_RESPONSE,
                DocumentContent.empty(), DocumentContent.empty(), TenderRequirements.empty(), List.of());
        int start = prompt.indexOf('{');
        var example = new ObjectMapper().readTree(prompt.substring(start, prompt.indexOf('}', start) + 1));
        assertThat(example.has("status")).isTrue();
        assertThat(example.has("conclusion")).isTrue();
        assertThat(example.has("suggestion")).isTrue();
        assertThat(example.has("reasonCode")).isTrue();
    }

    @Test
    void threeArbitraryTextChunksCannotCertifyCompositionOrTypography() {
        var bid = DocumentContent.fromText("bid.docx", "bh", "任意文字\f任意文字\f任意文字");
        assertThat(SubItemAndAttachmentRules.checkDocumentCompleteness(bid).status()).isNotEqualTo(QualityCheckStatus.PASS);
        assertThat(SubItemAndAttachmentRules.checkTemplateFormat(bid).status()).isNotEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    void contractReferenceOnSameLineDoesNotOwnScoringField() {
        var bid = DocumentContent.fromText("bid.docx", "bh", "售后服务方案附延保协议示例：购买日期_____年。\n服务承诺完整。");
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, TenderRequirements.empty()).stream())
                .noneMatch(r -> r.status() == QualityCheckStatus.FAIL);
    }

    @Test
    void explicitlyAdmittedFactInsideNumberedListCannotBeExemptedByDenialHeader() {
        var bid = DocumentContent.fromText("bid.pdf", "bh", "我方承诺不存在以下情形：\n1. 我司已弄虚作假提交材料。");
        assertThat(SubItemAndAttachmentRules.checkVetoKeyword(bid).status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void prohibitionOfSystemQuoteDoesNotBecomeSystemSubmissionInstruction() {
        var req = DeterministicPriceExtractor.extract("投标人不得在系统报价表中报折扣率，须提交纸质报价材料。");
        assertThat(req.pricingMethod()).isNotEqualTo(PriceRequirement.SYSTEM_DISCOUNT_METHOD);
    }

    @Test
    void realScoringPlaceholderWithEllipsisIsNotMistakenForCatalog() {
        var bid = DocumentContent.fromText("bid.pdf", "bh", "技术方案\n整体包括实施、交付……\n项目经理拟派：_____。");
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, TenderRequirements.empty()).orElseThrow().status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, requiredScoringField("项目经理拟派")).orElseThrow().status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void missingParsedPagesMustNotInventPhysicalPageOne() {
        var bid = new DocumentContent("bid.docx", "bh", "技术方案\n项目经理拟派：_____。", List.of());
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, TenderRequirements.empty()).stream())
                .flatMap(r -> r.evidences()).noneMatch(e -> Integer.valueOf(1).equals(e.pageNumber()));
    }

    @Test
    void modelCannotRestoreUnsupportedScoringFailureAfterRulesRejectContractBlank() {
        var bid = DocumentContent.fromText("bid.docx", "bh", "售后服务方案\n承诺全天服务。\n延保协议\n购买日期_____年。");
        var llm = new LlmAdjudicator(p -> LlmVerdict.success(new LlmVerdict("FAIL", "", "评分方案留白，不通过", "请填写留白")));
        var all = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(), TenderRequirements.empty(), Set.of(), llm, (p,s) -> { });
        assertThat(all.stream().filter(r -> r.itemCode().equals("SCORING_RESPONSE")).findFirst().orElseThrow().status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"技术方案：\n【请填写】", "技术方案\n响应时间：____小时"})
    void requestedFieldsAndSplitLinesCannotBecomeModelPass(String text) {
        var req = new TenderRequirements(List.of(new TenderRequirements.ScoringItem("技术方案", 5.0, "必须填写技术方案"),
                new TenderRequirements.ScoringItem("响应时间", 5.0,
                "必须填写响应时间，2小时以内得满分")), List.of(), List.of(), List.of(), List.of(), false);
        var llm = new LlmAdjudicator(p -> LlmVerdict.success(new LlmVerdict("PASS", "", "响应完整", "")));
        var all = BidDocumentQualityEngine.evaluateAll(DocumentContent.fromText("bid.docx", "bh", text),
                DocumentContent.empty(), List.of(), req, Set.of(), llm, (p,s) -> { });
        assertThat(all.stream().filter(r -> r.itemCode().equals("SCORING_RESPONSE")).findFirst().orElseThrow().status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    private static TenderRequirements requiredScoringField(String field) {
        return new TenderRequirements(List.of(new TenderRequirements.ScoringItem(field, 5.0, "必须填写" + field)),
                List.of(), List.of(), List.of(), List.of(), false);
    }

    @Test
    void topicFollowedByAnOwnStatementRequiresBindingConfirmation() {
        var bid = DocumentContent.fromText("bid.pdf", "bh", "我方承诺不存在以下情形：\n1. 弄虚作假：我司已进行材料伪造并提交。");
        assertThat(SubItemAndAttachmentRules.checkVetoKeyword(bid).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"另须随投标文件提供投标报价表。", "标段2须另附纸质总报价表，列明数量、单价和合价。"})
    void separatePaperTableObligationSurvivesSystemQuoteFusion(String extra) {
        String text = "标段1在系统报价表中报折扣率。" + extra;
        var fused = PriceRequirement.fuseWithDeterministic(PriceRequirement.standard(), DeterministicPriceExtractor.extract(text));
        assertThat(fused.requireBidOpeningTable()).isTrue();
        var tender = DocumentContent.fromText("t.pdf", "th", text);
        var failure = QualityCheckItemResult.fail(BidQualityCheckItem.BID_OPENING_TABLE, "未提供要求的报价表", "请补充", List.of());
        assertThat(PricingSubmissionPolicy.reconcile(failure, tender, fused).status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void systemQuoteSourceMustBeActualInstructionInsteadOfEarlierTemplateMention() {
        var tender = new DocumentContent("t.pdf", "th", "模板系统报价表。\n在系统报价表中报折扣率。", List.of(
                new DocumentContent.PageContent(1, "模板系统报价表。", List.of()),
                new DocumentContent.PageContent(2, "在系统报价表中报折扣率。", List.of())));
        var result = PricingSubmissionPolicy.reconcile(QualityCheckItemResult.fail(BidQualityCheckItem.BID_OPENING_TABLE,
                "无表", "复核", List.of()), tender, DeterministicPriceExtractor.extract(tender.fullText()));
        assertThat(result.evidences()).allSatisfy(e -> { assertThat(e.pageNumber()).isEqualTo(2);
            assertThat(e.quote()).isEqualTo("在系统报价表中报折扣率"); });
    }

    @Test
    void requiredFieldAfterCatalogIsStillChecked() {
        var req = new TenderRequirements(List.of(new TenderRequirements.ScoringItem("响应时间", 5.0, "请填写响应时间")),
                List.of(), List.of(), List.of(), List.of(), false);
        var bid = pages("目录\n技术方案......80", "响应时间：____小时");
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, req).orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void genericPaperQuoteTableIsNotHiddenBySystemChannel() {
        String text = "标段1在系统报价表中报折扣率。标段2还须提交报价表。";
        var req = PriceRequirement.fuseWithDeterministic(PriceRequirement.standard(), DeterministicPriceExtractor.extract(text));
        var fail = QualityCheckItemResult.fail(BidQualityCheckItem.BID_OPENING_TABLE, "未提供报价表", "补充", List.of());
        assertThat(PricingSubmissionPolicy.reconcile(fail, DocumentContent.fromText("t.pdf", "th", text), req).status())
                .isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void unknownTenderCannotCertifyBidderSuppliedDeviationRequirements() {
        var bid = DocumentContent.fromText("bid.docx", "bh", "商务偏离表\n| 条款 | 招标要求 | 投标响应 | 偏离情况 | 说明 |\n"
                + "| 交货期 | 30天 | 30天 | 无偏离 | 满足要求 |");
        var llm = new LlmAdjudicator(p -> LlmVerdict.success(new LlmVerdict("PASS", "", "完全响应", "")));
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(), TenderRequirements.empty(), Set.of(), llm, (p,s)->{});
        assertThat(result.stream().filter(x -> x.itemCode().equals("RESPONSIVENESS_REVIEW")).findFirst().orElseThrow().status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void actualTenderBoundCompliantAndNegativeRowsKeepDistinctVerdicts() {
        var tender = DocumentContent.fromText("t.pdf", "th", "交货期30天。");
        for (String type : List.of("无偏离", "负偏离")) {
            var bid = DocumentContent.fromText("bid.docx", "bh", "商务偏离表\n| 条款 | 招标要求 | 投标响应 | 偏离情况 | 说明 |\n"
                    + "| 交货期 | 30天 | 30天 | " + type + " | 满足要求 |");
            var result = ResponsivenessReviewPolicy.check(bid, tender);
            assertThat(result.status()).isEqualTo(type.equals("无偏离") ? QualityCheckStatus.PASS : QualityCheckStatus.FAIL);
        }
    }

    @Test
    void qualificationDeclarationFieldSplitAcrossLinesRemainsBlocked() {
        var bid = DocumentContent.fromText("bid.docx", "bh", "资格声明：\n【请填写】");
        var result = QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), TenderRequirements.empty(), List.of());
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void genericDescriptionLabelCannotBindBySubstringOfScoringProse() {
        var req = new TenderRequirements(List.of(new TenderRequirements.ScoringItem("售后服务方案",5.0,"请说明服务范围")),
                List.of(),List.of(),List.of(),List.of(),false);
        var bid = DocumentContent.fromText("bid.docx", "bh", "补充附件\n说明：_____");
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, req).stream()).noneMatch(x -> x.status() == QualityCheckStatus.FAIL);
    }

    @Test
    void legacyNullableScoringTextIsUnknownRatherThanCrashOrProvenDefect() {
        var req = new TenderRequirements(List.of(new TenderRequirements.ScoringItem(null,5.0,null)),
                List.of(),List.of(),List.of(),List.of(),false);
        var bid = DocumentContent.fromText("bid.docx", "bh", "补充附件\n说明：_____");
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, req).stream()).noneMatch(x -> x.status() == QualityCheckStatus.FAIL);
    }

    @org.junit.jupiter.params.provider.CsvSource(value = {
            "质保期1年6个月。|质保期|1年",
            "1.5年。|1|5年",
            "第1条质保期3年。第2条质保期1年。|1|质保期1年",
            "本项目交货期30天。历史合同交货期5天，仅供参考。|交货期|5天"
    }, delimiter = '|')
    @ParameterizedTest
    void truncatedOtherClauseOrReferenceQuoteCannotProveCurrentObligation(String source, String clause, String requirement) {
        var tender = DocumentContent.fromText("t.pdf", "th", source);
        var bid = DocumentContent.fromText("bid.docx", "bh", "商务偏离表\n| 条款 | 招标要求 | 投标响应 | 偏离情况 | 说明 |\n"
                + "| " + clause + " | " + requirement + " | " + requirement + " | 无偏离 | 满足要求 |");
        assertThat(ResponsivenessReviewPolicy.check(bid, tender).status()).isNotEqualTo(QualityCheckStatus.PASS);
        var llm = new LlmAdjudicator(p -> LlmVerdict.success(new LlmVerdict("PASS","","完全响应","")));
        var results = BidDocumentQualityEngine.evaluateAll(bid,tender,List.of(),TenderRequirements.empty(),Set.of(),llm,(p,s)->{});
        assertThat(results.stream().filter(x -> x.itemCode().equals("RESPONSIVENESS_REVIEW")).findFirst().orElseThrow().status())
                .isNotEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    void authoritativeItemizedMaterialFlagCannotBeOverriddenByFormNameDictionary() {
        String text = "标段1在系统报价表中报折扣率。标段2须另附分项价格清单。";
        var req = PriceRequirement.fuseWithDeterministic(PriceRequirement.standard(), DeterministicPriceExtractor.extract(text));
        var failure = QualityCheckItemResult.fail(BidQualityCheckItem.BID_OPENING_TABLE,"缺少分项价格清单","请补充",List.of());
        assertThat(PricingSubmissionPolicy.reconcile(failure,DocumentContent.fromText("t.pdf","th",text),req).status())
                .isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void modelCannotCertifyMissingPaperQuoteAgainstRequiredMaterialFlag() {
        String text = "标段1在系统报价表中报折扣率。标段2须另附分项价格清单。";
        var req = TenderRequirements.empty().withPriceRequirement(PriceRequirement.fuseWithDeterministic(
                PriceRequirement.standard(), DeterministicPriceExtractor.extract(text)));
        var llm = new LlmAdjudicator(p -> LlmVerdict.success(new LlmVerdict("PASS","","材料齐全","")));
        var results = BidDocumentQualityEngine.evaluateAll(DocumentContent.fromText("b.docx","bh","承诺按要求报价"),
                DocumentContent.fromText("t.pdf","th",text),List.of(),req,Set.of(),llm,(p,s)->{});
        assertThat(results.stream().filter(x -> x.itemCode().equals("BID_OPENING_TABLE")).findFirst().orElseThrow().status())
                .isNotEqualTo(QualityCheckStatus.PASS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"；", ";", "。"})
    void completedQualificationClauseCannotOwnLaterContractBlank(String separator) {
        var bid = DocumentContent.fromText("bid.docx", "bh",
                "资格声明：我司满足所有资格条件" + separator + "合同示例：甲方_____");
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(),
                TenderRequirements.empty(), List.of()).stream()).noneMatch(r -> r.status() == QualityCheckStatus.FAIL);
        var required = DocumentContent.fromText("bid.docx", "bh", "资格声明：_____");
        assertThat(QualificationVetoPolicy.checkFirstStage(required, DocumentContent.empty(),
                TenderRequirements.empty(), List.of()).orElseThrow().status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"在系统报价表中不得填报折扣率", "在系统报价表中无需填报折扣率",
            "在系统报价表中禁止填报折扣率", "在系统报价表中不应录入折扣率"})
    void negativeSubmissionActionCannotChangePricingMethodOrFailure(String text) {
        var req = DeterministicPriceExtractor.extract(text);
        assertThat(PricingSubmissionPolicy.hasSystemDiscountInstruction(text)).isFalse();
        assertThat(req.pricingMethod()).isNotEqualTo(PriceRequirement.SYSTEM_DISCOUNT_METHOD);
        var failure = QualityCheckItemResult.fail(BidQualityCheckItem.BID_OPENING_TABLE, "缺少报价", "核对", List.of());
        assertThat(PricingSubmissionPolicy.reconcile(failure, DocumentContent.fromText("t.pdf", "th", text), req))
                .isEqualTo(failure);
        assertThat(PricingSubmissionPolicy.hasSystemDiscountInstruction("在系统报价表中必须填报折扣率")).isTrue();
    }

    private static DocumentContent pages(String first, String second) {
        return new DocumentContent("bid.docx", "hash", first + "\n" + second,
                List.of(new DocumentContent.PageContent(63, first, List.of()), new DocumentContent.PageContent(80, second, List.of())));
    }
}
