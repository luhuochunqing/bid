package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** 报告144真实上下文脱敏验收；虚拟文本页号不作为原DOCX物理分页证明。 */
class Quality1360BusinessAcceptanceTest {
    private static final String TENDER = "第三卷 经济标（标段1在系统报价表中报折扣率。"
            + "商务标、技术标部分不含折扣率报价；标段2不作价格评审，但也要在系统报价表中报折扣率。）"
            + "折扣率=100×推送价格/原价，单位为%，请忽略系统报价单模板中的单位元。";
    private static final String BID = "售后服务方案\n承诺提供7×24小时服务及全国售后保障。\n"
            + "延保服务\n签订的延保协议生效起，在延保期内享有约定服务。\n延保服务协议书\n"
            + "甲方：供应商\n乙方：\n供应商于_____年____月____日在（公司）购买________，"
            + "经双方达成一致，对该产品延长质保期至_____年____月____日。\n"
            + "注：上述材料所填信息务必真实有效！否则将按弄虚作假进行处罚！\n"
            + "初步评审承诺函\n我方承诺不存在以下情形之一：\n"
            + "1. 被列入失信名单；\n5. 不满足招标文件对投标人资质、业绩、委托授权书等资格要求；\n"
            + "7. 投标人有串通投标、弄虚作假、行贿等违法行为；\n"
            + "经济标\n承诺函\n具体折扣率详见系统报价表。\n"
            + "本次投标联系人：张某 联系电话：13800138000";

    @Test
    void sixReportedFailuresCannotBeRecreatedFromUnrelatedBlanksAndDenials() {
        var bid = DocumentContent.fromText("bid.docx", "business-bid", BID);
        var tender = new DocumentContent("tender.pdf", "business-tender", TENDER,
                List.of(new DocumentContent.PageContent(7, TENDER, List.of())));
        var requirements = new TenderRequirements(List.of(),
                List.of("投标人具有有效的中华人民共和国增值电信业务经营许可证"), List.of(), List.of(), List.of(), false)
                .withPriceRequirement(DeterministicPriceExtractor.extract(TENDER));
        for (String status : List.of("PASS", "FAIL")) {
            var llm = new LlmAdjudicator(p -> LlmVerdict.success(new LlmVerdict(status, "", "模型判断", "")));
            var all = BidDocumentQualityEngine.evaluateAll(bid, tender, List.of(), requirements, Set.of(), llm, (p, s) -> { })
                    .stream().map(r -> QualificationDetailReconciler.reconcile(r,
                            QualificationMatchPolicy.buildDetails(requirements.certificateRequirements(), List.of(), bid,
                                    java.time.LocalDate.of(2026, 11, 6)), requirements, bid))
                    .collect(Collectors.toMap(QualityCheckItemResult::itemCode, Function.identity()));
            for (String code : List.of("QUALIFICATION_MATCH", "QUALIFICATION_VETO", "SCORING_RESPONSE",
                    "RESPONSIVENESS_REVIEW", "VETO_KEYWORD", "BID_OPENING_TABLE")) {
                assertThat(all.get(code).status()).as("report144 %s / model %s", code, status)
                        .isNotEqualTo(QualityCheckStatus.FAIL);
            }
            for (String code : List.of("QUALIFICATION_MATCH", "QUALIFICATION_VETO", "SCORING_RESPONSE")) {
                assertThat(all.get(code).status()).as("unproved %s / %s", code, status)
                        .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            }
            assertThat(all.get("BID_OPENING_TABLE").status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(all.get("BID_OPENING_TABLE").evidences()).allSatisfy(e -> {
                assertThat(e.pageNumber()).isEqualTo(7);
                assertThat(TENDER).contains(e.quote());
            });
            assertThat(all.get("CONTACT_INFO").status()).isEqualTo(QualityCheckStatus.PASS);
        }
    }

    @Test
    void knownTextFactsStayUsefulWhileUnprovedImagesAndLayoutStayManual() {
        var bid = DocumentContent.fromText("bid.docx", "business-bid", BID + "\n授权委托书\n【已附图片】");
        var all = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(), TenderRequirements.empty(), Set.of())
                .stream().collect(Collectors.toMap(QualityCheckItemResult::itemCode, Function.identity()));
        assertThat(all.get("CONTACT_INFO").status()).isEqualTo(QualityCheckStatus.PASS);
        for (String code : List.of("ID_AUTH_LETTER", "DOCUMENT_COMPLETENESS", "TEMPLATE_FORMAT")) {
            assertThat(all.get(code).status()).as(code).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void confirmedDefectsAreNotTurnedIntoPassingResults() {
        var bid = DocumentContent.fromText("bid.docx", "b", "服务协议：_____。\n我司已弄虚作假提交材料。");
        var req = new TenderRequirements(List.of(new TenderRequirements.ScoringItem("服务方案", 5.0,
                "要求投标人必须填写服务协议")), List.of(), List.of(), List.of(), List.of(), false);
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, req).orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(SubItemAndAttachmentRules.checkVetoKeyword(bid).status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(ResponsivenessReviewPolicy.check(DocumentContent.fromText("deviation.docx", "d",
                "商务偏离表：付款方式存在负偏离，无法满足30天付款要求。")).status())
                .isEqualTo(QualityCheckStatus.FAIL);
        var tender = DocumentContent.fromText("t.pdf", "t", TENDER + "另须提供开标一览表。");
        var price = DeterministicPriceExtractor.extract(tender.fullText());
        var failed = com.xiyu.bid.compliance.domain.QualityCheckItemResult.fail(
                com.xiyu.bid.compliance.domain.BidQualityCheckItem.BID_OPENING_TABLE, "缺实际表格", "补充", List.of());
        assertThat(PricingSubmissionPolicy.reconcile(failed, bid, tender, price)).isEqualTo(failed);
    }
}
