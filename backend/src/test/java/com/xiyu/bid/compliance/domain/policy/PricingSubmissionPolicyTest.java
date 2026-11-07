package com.xiyu.bid.compliance.domain.policy;

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

/** 完整报价对象肯定证明及历史动作/来源/载体保护；未知不得声明SYS。 */
class PricingSubmissionPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "在系统报价表中填报折扣率", "在系统报价表中填报含税折扣率",
            "在系统报价表中填报不含税折扣率", "在系统报价表中填报不同折扣率",
            "在系统报价表的不含税栏目中填写折扣率", "在系统报价表中按不含税口径填报折扣率",
            "在 系统报价表 中\n填报 不含税折扣率", "通过系统报价单录入不含税折扣率",
            "在系统报价表中不得填报总金额，但应填报折扣率",
            "在系统报价表中不得填报总金额但应填报折扣率",
            "在系统报价表的不可抵扣栏目中填写折扣率", "在系统报价表中按不得漏填要求填报折扣率"})
    void positiveActionIsNotNegatedByItsObjectOrColumn(String text) {
        assertThat(PricingSubmissionPolicy.hasSystemDiscountInstruction(text)).isTrue();
        assertThat(PricingSubmissionPolicy.systemDiscountSource(text)).isEqualTo(text);
        var price = DeterministicPriceExtractor.extract(text);
        assertThat(price.pricingMethod()).isEqualTo(PriceRequirement.SYSTEM_DISCOUNT_METHOD);
        var tender = new DocumentContent("tender.pdf", "th", "模板提及系统报价表。\n" + text, List.of(
                new DocumentContent.PageContent(1, "模板提及系统报价表。", List.of()),
                new DocumentContent.PageContent(80, text, List.of())));
        var bid = DocumentContent.fromText("bid.docx", "bh", "经济标承诺：具体折扣率在系统提交。");
        for (String verdict : List.of("PASS", "FAIL")) {
            var llm = new LlmAdjudicator(p -> LlmVerdict.success(new LlmVerdict(verdict, "", "模型判断报价表", "")));
            var result = BidDocumentQualityEngine.evaluateAll(bid, tender, List.of(),
                    TenderRequirements.empty().withPriceRequirement(price), Set.of(), llm, (p, s) -> { })
                    .stream().filter(r -> r.itemCode().equals("BID_OPENING_TABLE")).findFirst().orElseThrow();
            assertThat(result.status()).as("model %s / %s", verdict, text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(result.evidences()).isNotEmpty().allSatisfy(e -> {
                assertThat(e.sourceType()).isEqualTo("TENDER");
                assertThat(e.pageNumber()).isEqualTo(80);
                assertThat(e.quote()).isEqualTo(text);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "在系统报价表中不得填报不含税折扣率", "在系统报价表中无需填报折扣率",
            "在系统报价表中禁止填写含税折扣率", "在系统报价表中不应录入折扣率",
            "不得在系统报价表中填报不含税折扣率", "不必在系统报价表中填报折扣率",
            "在系统报价表中不得擅自填报折扣率", "在系统报价表中不得在备注中填报折扣率",
            "在系统报价表中不得直接填报折扣率", "禁止投标人在系统报价表中填报折扣率",
            "严禁在系统报价表中填报不含税折扣率", "在系统报价表中不要填报不含税折扣率",
            "在系统报价表中不许填报折扣率", "禁止所有参加本次招标的供应商在系统报价表中填报折扣率",
            "在系统报价表中报价人不得填报折扣率",
            "在任何情况下不得在系统报价表中填报折扣率",
            "在本次投标中禁止在系统报价表中填报折扣率",
            "在系统报价表中不得在备注栏目中填报折扣率"})
    void negatedActionCannotCreatePositiveChannel(String text) {
        assertThat(PricingSubmissionPolicy.hasSystemDiscountInstruction(text)).isFalse();
        var price = DeterministicPriceExtractor.extract(text);
        assertThat(price.pricingMethod()).isNotEqualTo(PriceRequirement.SYSTEM_DISCOUNT_METHOD);
        var failure = QualityCheckItemResult.fail(BidQualityCheckItem.BID_OPENING_TABLE,
                "独立报价核查失败", "核对材料", List.of());
        assertThat(PricingSubmissionPolicy.reconcile(failure,
                DocumentContent.fromText("tender.pdf", "th", text), price)).isEqualTo(failure);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "在指定系统报价表中填报折扣率。",
            "若采用线上报价，则在系统报价表中填报折扣率。",
            "若采用线上报价，但应在系统报价表中填报折扣率。",
            "尚未要求线上报价，但应在系统报价表中填报折扣率。",
            "若采用线上报价，\n则在 系统报价表 中填报折扣率。",
            "若采用线上报价，在系统报价表中填报折扣率。",
            "如果采用线上报价，则在系统报价表中填报折扣率。",
            "尚未要求投标人在系统报价表中填报折扣率。",
            "根据尚待确认的通知，在系统报价表中填报折扣率。",
            "在系统报价表中在规定时间内填报不含税折扣率",
            "在系统报价表中于投标截止时间前填写折扣率",
            "在系统报价表中在三日内填报含税折扣率",
            "在系统报价表中于招标文件规定的时间内填报折扣率",
            "在系统报价表中在系统开放期间填报折扣率",
            "在系统报价表中于招标文件中规定的期限内填报折扣率",
            "在系统报价表中应于系统填报截止前填报折扣率",
            "在系统报价表中应于平台报送截止前填写折扣率",
            "在系统报价表中核对信息，另在纸质附件中填报折扣率",
            "在系统报价表中核对信息但应在纸质附件中填报折扣率",
            "在系统报价表中核对信息，通过纸质附件报送折扣率",
            "在系统报价表中核对信息，另于投标文件中填报折扣率",
            "在系统报价表中核对信息，另通过其他平台报送折扣率",
            "在系统报价表中填报金额而非折扣率。",
            "在系统报价表中填报折扣率以外的金额。",
            "在系统报价表中核对信息，另在纸质附件中按时填报折扣率。",
            "在系统报价表中核对信息，另在纸质附件中于截止前填报折扣率"})
    void unresolvedConditionsCannotCertifyAnySubmissionChannel(String text) {
        assertThat(PricingSubmissionPolicy.hasSystemDiscountInstruction(text)).isFalse();
        assertThat(PricingSubmissionPolicy.systemDiscountSource(text)).isEmpty();
        var price = DeterministicPriceExtractor.extract(text);
        assertThat(price.pricingMethod()).isNotEqualTo(PriceRequirement.SYSTEM_DISCOUNT_METHOD);
        var tender = new DocumentContent("tender.pdf", "th", text,
                List.of(new DocumentContent.PageContent(80, text, List.of())));
        for (String verdict : List.of("PASS", "FAIL")) {
            var llm = new LlmAdjudicator(p -> LlmVerdict.success(new LlmVerdict(verdict, "", "模型报价结论", "")));
            var result = BidDocumentQualityEngine.evaluateAll(
                    DocumentContent.fromText("bid.docx", "bh", "经济标承诺：具体折扣率在系统提交。"),
                    tender, List.of(), TenderRequirements.empty().withPriceRequirement(price), Set.of(), llm, (p, s) -> { })
                    .stream().filter(r -> r.itemCode().equals("BID_OPENING_TABLE")).findFirst().orElseThrow();
            assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(result.conclusion()).contains("尚未可靠绑定");
            assertThat(result.evidences()).isNotEmpty().allSatisfy(e -> {
                assertThat(e.pageNumber()).isEqualTo(80);
                assertThat(e.quote()).isEqualTo(text.substring(0, text.endsWith("。") ? text.length() - 1 : text.length()));
                assertThat(e.sourceTitle()).contains("待确认");
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"在非系统报价表中填报折扣率", "在非系统报价单中填写折扣率",
            "在纸质而非系统报价表中填报折扣率", "在 非系统报价表 中填报折扣率"})
    void excludedSystemCarrierCannotRewritePaperFailure(String text) {
        assertThat(PricingSubmissionPolicy.hasSystemDiscountInstruction(text)).isFalse();
        assertThat(PricingSubmissionPolicy.systemDiscountSource(text)).isEmpty();
        String tenderText = text + "。投标文件须提交报价表。";
        var price = DeterministicPriceExtractor.extract(tenderText);
        assertThat(price.pricingMethod()).isNotEqualTo(PriceRequirement.SYSTEM_DISCOUNT_METHOD);
        var failure = QualityCheckItemResult.fail(BidQualityCheckItem.BID_OPENING_TABLE,
                "纸面报价缺失", "核对原件", List.of());
        assertThat(PricingSubmissionPolicy.reconcile(failure,
                DocumentContent.fromText("tender.pdf", "th", tenderText), price)).isEqualTo(failure);
    }

    @Test
    void contrastResetsProhibitionBeforeChannelAndKeepsOriginalSource() {
        String text = "不得在系统报价表中填报总金额，但应填报折扣率";
        assertThat(PricingSubmissionPolicy.hasSystemDiscountInstruction(text)).isTrue();
        assertThat(PricingSubmissionPolicy.systemDiscountSource(text))
                .isEqualTo("在系统报价表中填报总金额，但应填报折扣率");
    }

    @ParameterizedTest
    @ValueSource(strings = {"不得报总金额，但应", "不得报总金额但应"})
    void negativeEarlierClauseDoesNotNegateNewPositiveInstruction(String prefix) {
        String positive = "在系统报价表中填报不含税折扣率";
        String text = prefix + positive;
        assertThat(PricingSubmissionPolicy.hasSystemDiscountInstruction(text)).isTrue();
        assertThat(PricingSubmissionPolicy.systemDiscountSource(text)).isEqualTo(positive);
        assertThat(DeterministicPriceExtractor.extract(text).pricingMethod())
                .isEqualTo(PriceRequirement.SYSTEM_DISCOUNT_METHOD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"。", "；"})
    void laterClauseCannotSupplyTheChannelsQuotationAction(String separator) {
        String text = "在系统报价表中核对信息" + separator + "另处填报折扣率";
        assertThat(PricingSubmissionPolicy.hasSystemDiscountInstruction(text)).isFalse();
        assertThat(DeterministicPriceExtractor.extract(text).pricingMethod())
                .isNotEqualTo(PriceRequirement.SYSTEM_DISCOUNT_METHOD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"含税折扣率", "不含税折扣率"})
    void positiveSystemActionDoesNotWaiveIndependentPaperObligations(String object) {
        String text = "在系统报价表中填报" + object + "。投标文件还须另附开标一览表及分项报价明细表。";
        var price = PriceRequirement.fuseWithDeterministic(PriceRequirement.standard(),
                DeterministicPriceExtractor.extract(text));
        assertThat(price.requireBidOpeningTable()).isTrue();
        assertThat(price.requireItemizedList()).isTrue();
        var failure = QualityCheckItemResult.fail(BidQualityCheckItem.BID_OPENING_TABLE,
                "纸面报价核查失败", "补齐纸面材料", List.of());
        assertThat(PricingSubmissionPolicy.reconcile(failure,
                DocumentContent.fromText("tender.pdf", "th", text), price)).isEqualTo(failure);
    }
    @ParameterizedTest
    @ValueSource(strings = {"无需提交报价表", "无需提交分项报价明细表"})
    void explicitWaiverCannotBecomeMissingPaperFailure(String waiver) {
        String text = "在系统报价表中填报折扣率。" + waiver + "。";
        var price = DeterministicPriceExtractor.extract(text);
        assertThat(price.requireBidOpeningTable()).isFalse();
        if (waiver.contains("分项")) assertThat(price.requireItemizedList()).isFalse();
        else assertThat(price.requireItemizedList()).isNull();
        var failure = QualityCheckItemResult.fail(BidQualityCheckItem.BID_OPENING_TABLE,
                "缺少报价表", "核对材料", List.of());
        var result = PricingSubmissionPolicy.reconcile(failure,
                DocumentContent.fromText("tender.pdf", "th", text), price);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.evidences()).anySatisfy(e -> assertThat(e.quote()).contains(waiver));
        for (String verdict : List.of("PASS", "FAIL")) {
            var llm = new LlmAdjudicator(p -> LlmVerdict.success(new LlmVerdict(verdict, "", "模型报价结果", "")));
            var engineResult = BidDocumentQualityEngine.evaluateAll(
                    DocumentContent.fromText("bid.docx", "bh", "折扣率在系统提交。"),
                    DocumentContent.fromText("tender.pdf", "th", text), List.of(),
                    TenderRequirements.empty().withPriceRequirement(price), Set.of(), llm, (p, r) -> { })
                    .stream().filter(r -> r.itemCode().equals("BID_OPENING_TABLE")).findFirst().orElseThrow();
            assertThat(engineResult.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(engineResult.evidences()).anySatisfy(e -> {
                assertThat(e.pageNumber()).isEqualTo(1);
                assertThat(e.quote()).contains(waiver);
            });
        }
        var conflicting = new PriceRequirement(true, null, null, price.pricingMethod(), true, true, "模型提取");
        assertThat(PricingSubmissionPolicy.reconcile(failure,
                DocumentContent.fromText("tender.pdf", "th", text), conflicting).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void mereTableReferenceDoesNotEstablishAnObligation() {
        var price = DeterministicPriceExtractor.extract("报价表和分项报价明细表仅为模板名称。");
        assertThat(price.requireBidOpeningTable()).isNull();
        assertThat(price.requireItemizedList()).isNull();
    }

    @Test
    void separateExplicitObligationStillRequiresPaperDespiteOtherWaiver() {
        String text = "在系统报价表中填报折扣率。无需提交报价表。另须提供开标一览表。";
        var price = DeterministicPriceExtractor.extract(text);
        assertThat(price.requireBidOpeningTable()).isTrue();
        var failure = QualityCheckItemResult.fail(BidQualityCheckItem.BID_OPENING_TABLE, "缺表", "核对", List.of());
        assertThat(PricingSubmissionPolicy.reconcile(failure,
                DocumentContent.fromText("tender.pdf", "th", text), price)).isEqualTo(failure);
    }

}
