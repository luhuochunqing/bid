package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class R7EvidenceRoleTest {
    private static QualityCheckItemResult finalResponsiveness(String text) {
        var bid = DocumentContent.fromText("bid", "hash", text);
        var llm = new LlmAdjudicator(prompt -> LlmVerdict.success(new LlmVerdict("PASS", "", "全部满足", "")));
        return BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(), TenderRequirements.empty(),
                Set.of(), llm, (p, s) -> { }).stream()
                .filter(x -> x.itemCode().equals(BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode()))
                .findFirst().orElseThrow();
    }
    @Test
    void rulesAreNotOwnFactsEvenWhenTheyMentionActualBidder() {
        for (String text : List.of("评审标准：投标人实际收入低于4亿元不得分",
                "我方已理解评审规则：投标人实际收入低于4亿元不得分",
                "我方确认招标文件要求投标人实际收入低于4亿元不得分")) {
            assertThat(finalResponsiveness(text).status()).as(text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }
    @Test
    void ambiguousOrConflictingTableCannotBeUpgradedByModel() {
        for (String description : List.of("本企业收入3亿元，不能达到招标要求4亿元",
                "我方实际收入仅有3亿元，达不到4亿元的最低要求", "我方承诺不能满足招标要求")) {
            String text = "商务偏离表\n| 序号 | 条款号 | 招标参数要求 | 投标响应参数 | 偏离情况 | 说明 |\n"
                    + "| 1 | 2.1 | 收入不得低于4亿元 | 响应 | 无偏离 | " + description + " |";
            assertThat(finalResponsiveness(text).status()).as(description).isNotEqualTo(QualityCheckStatus.PASS);
        }
    }
    @Test
    void actualOwnShortfallStillCannotPass() {
        for (String text : List.of("本企业营业收入为3亿元，低于招标最低要求4亿元", "我方实际收入3亿元，低于招标要求4亿元")) {
            assertThat(finalResponsiveness(text).status()).as(text).isNotEqualTo(QualityCheckStatus.PASS);
        }
    }
    @Test
    void genuineCertificateAndExplanationMustNotDisappear() {
        for (String text : List.of("投标人须提供未被吊销的食品经营许可证并作情况说明",
                "投标人必须提供未被吊销的营业许可证及相关说明",
                "投标人应提交未被注销的安全生产许可证以及使用说明书")) {
            var req = TenderRequirements.empty().withMergedQualificationConditions(List.of(new QualificationCondition(text, null, null)));
            assertThat(req.certificateRequirements()).as(text).isNotEmpty();
            var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "满足", List.of());
            assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), req,
                    DocumentContent.fromText("b", "h", "未提供所要求许可证")).status()).as(text)
                    .isNotEqualTo(QualityCheckStatus.PASS);
        }
    }
    @Test
    void purePenaltyProofDoesNotBecomeCertificate() {
        for (String text : List.of("供应商须提交未被吊销许可证的证明材料", "供应商不具有被吊销许可证的情形")) {
            var q = new QualificationCondition(text, null, null);
            assertThat(q.text()).isEqualTo(text);
            assertThat(QualificationCondition.certificateQueries(List.of(q))).as(text).isEmpty();
        }
    }
    @Test
    void certificateAndPenaltyProofAreSeparateObjects() {
        String text = "应提供食品经营许可证同时提供不存在被吊销许可证情况的证明材料";
        var req = TenderRequirements.empty().withMergedQualificationConditions(List.of(new QualificationCondition(text, null, null)));
        assertThat(req.certificateRequirements()).hasSize(1);
        assertThat(req.certificateRequirements().get(0)).contains("食品经营许可证");
    }
    @Test
    void identicalBidderSuppliedRequirementWithoutTenderNeedsReview() {
        String text = "商务偏离表\n| 序号 | 条款号 | 招标参数要求 | 投标响应参数 | 偏离情况 | 说明 |\n"
                + "| 1 | 2.1 | 质保3年 | 质保3年 | 无偏离 | / |";
        assertThat(finalResponsiveness(text).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }
    @Test
    void mixedRuleAndOwnFactsMustNotPass() {
        for (String text : List.of("商务偏离表：招标要求不得低于4亿，我方实际收入低于3亿，无偏离。",
                "商务偏离表：我方实际收入达不到4亿元，声明无偏离。",
                "我方承诺无负偏离，但实际营业收入低于招标要求",
                "我方实际收入3亿元，未达到最低要求4亿元", "我方收入未能达到最低要求")) {
            assertThat(finalResponsiveness(text).status()).as(text).isNotEqualTo(QualityCheckStatus.PASS);
        }
    }
    @Test
    void tableConflictsAndUnknownDescriptionsMustNotPass() {
        for (String[] row : List.of(new String[]{"无偏离", "我方确认评审标准，实际收入未达到4亿元"},
                new String[]{"无偏离", "我方知悉招标文件要求，实际收入达不到4亿元"},
                new String[]{"正偏离", "是否满足尚待核实"},
                new String[]{"无负偏离，部分满足", "满足"})) {
            String text = "商务偏离表\n| 序号 | 条款号 | 招标参数要求 | 投标响应参数 | 偏离情况 | 说明 |\n"
                    + "| 1 | 2.1 | 收入不得低于4亿元 | 响应 | " + row[0] + " | " + row[1] + " |";
            assertThat(finalResponsiveness(text).status()).as(text).isNotEqualTo(QualityCheckStatus.PASS);
        }
    }
    @Test
    void citationByBidderIsNotAnAdmissionOfShortfall() {
        for (String text : List.of("我方提供如下评审引用：投标人收入低于4亿元不得分。",
                "我方承诺遵照执行：投标人收入低于4亿元不得分。")) {
            assertThat(finalResponsiveness(text).status()).as(text).isNotEqualTo(QualityCheckStatus.FAIL);
        }
    }
    @Test
    void certificateAndSeparateOrAlternativeProofMustNotVanish() {
        for (String text : List.of("投标人须提供有效的食品经营许可证、未被吊销证明材料",
                "投标人须提供未被吊销的食品经营许可证或情况说明",
                "投标人须附有效的食品经营许可证并证明其未被吊销",
                "投标人应附未被吊销之安全生产许可证复印件",
                "投标人应附带有效的许可证且不得被吊销")) {
            var req = TenderRequirements.empty().withMergedQualificationConditions(List.of(new QualificationCondition(text, null, null)));
            var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "满足", List.of());
            assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), req,
                    DocumentContent.fromText("b", "h", "未提供所要求许可证")).status()).as(text)
                    .isNotEqualTo(QualityCheckStatus.PASS);
        }
    }
    @Test
    void optionalCertificateTemplateIsNotAProvenMandatoryDefect() {
        var req = TenderRequirements.empty().withMergedQualificationConditions(List.of(
                new QualificationCondition("食品经营许可证（如有则提供）", null, null)));
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "满足", List.of());
        var bid = DocumentContent.fromText("b", "h", "无此证书，无需提供。\n食品经营许可证（如有则提供）：【此处上传许可证】");
        assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), req, bid).status())
                .isNotEqualTo(QualityCheckStatus.FAIL);
        var generic = DocumentContent.fromText("b", "h", "无此证书，无需提供。\n食品经营许可证：【此处上传许可证】");
        assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), req, generic).status())
                .isNotEqualTo(QualityCheckStatus.FAIL);
    }
    @Test
    void optionalUnrelatedCertificateDoesNotHideMandatoryPlaceholder() {
        var req = TenderRequirements.empty().withMergedQualificationConditions(List.of(
                new QualificationCondition("投标人须具有安全生产许可证", null, null),
                new QualificationCondition("ISO14001认证（如有则提供）", null, null)));
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "满足", List.of());
        var bid = DocumentContent.fromText("b", "h", "安全生产许可证：【此处上传许可证】");
        assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), req, bid).status())
                .isEqualTo(QualityCheckStatus.FAIL);
    }
}
