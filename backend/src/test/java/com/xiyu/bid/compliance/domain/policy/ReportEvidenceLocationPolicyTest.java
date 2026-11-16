package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.*;
import com.xiyu.bid.compliance.service.SidecarPayloadParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ReportEvidenceLocationPolicyTest {
    private QualityCheckItemResult result(Integer page, String quote) {
        return QualityCheckItemResult.fail(BidQualityCheckItem.RESPONSIVENESS_REVIEW, "触发", "核对",
                List.of(QualityEvidence.bid("触发原文", page, quote)));
    }
    @Test
    void shortAndLongUnpaginatedTextHaveNoPhysicalPage() {
        for (String text : List.of("触发内容", "正文".repeat(5000) + "触发内容")) {
            var doc = SidecarPayloadParser.buildDocumentContent("bid.docx", "h", text);
            var hit = doc.pages().stream().filter(p -> p.text().contains("触发内容")).findFirst().orElseThrow();
            var out = ReportEvidenceLocationPolicy.project(result(hit.pageNumber(), "触发内容"), doc, null);
            assertThat(out.evidences().getFirst().pageNumber()).isNull();
            assertThat(out.evidences().getFirst().quote()).isEqualTo("触发内容");
            assertThat(out.evidences().getFirst().sourceFileName()).isEqualTo("bid.docx");
        }
    }
    @Test
    void explicitBlankPageDoesNotShiftFollowingPage() {
        var doc = SidecarPayloadParser.buildDocumentContent("bid.pdf", "h", "封面\f\f触发内容");
        assertThat(ReportEvidenceLocationPolicy.project(result(3, "触发内容"), doc, null)
                .evidences().getFirst().pageNumber()).isEqualTo(3);
    }
    @Test
    void mergedInternalIdRetainsBookLocalPageAndIdentity() {
        var a = new DocumentContent("商务.pdf", "a", "共同词", List.of(new DocumentContent.PageContent(7, "共同词", List.of())));
        var b = new DocumentContent("技术.pdf", "b", "共同词", List.of(new DocumentContent.PageContent(80, "共同词", List.of())));
        var merged = DocumentContent.merge(List.of(a, b));
        var out = ReportEvidenceLocationPolicy.project(result(2, "共同词"), merged, null).evidences().getFirst();
        assertThat(out.pageNumber()).isEqualTo(80);
        assertThat(out.sourceFileName()).isEqualTo("技术.pdf");
        assertThat(out.sourceFileHash()).isEqualTo("b");
    }
    @Test
    void mergedImagesFollowInternalPageMappingWhenPhysicalPagesAreSparse() {
        var doc = new DocumentContent("扫描.pdf", "h", "正文", List.of(
                new DocumentContent.PageContent(3, "正文", List.of())), List.of(
                new PageImage(1, "image/png", "证书", 3, 1, 1, "")));
        var merged = DocumentContent.merge(List.of(doc, doc));
        assertThat(merged.images()).extracting(PageImage::pageHint).containsExactly(1, 2);
        var item = QualityCheckItemResult.manualReview(BidQualityCheckItem.SIGNATURE_SEAL, "核对", "核对",
                List.of(new QualityEvidence("BID_IMAGE", "图像", 2, "视觉观察", null, 0.9, null, null)));
        assertThat(ReportEvidenceLocationPolicy.project(item, merged, null).evidences().getFirst().pageNumber()).isEqualTo(3);
    }

    @Test
    void wrongQuoteCannotBeRelocatedToAnEarlierMatchingPage() {
        var doc = new DocumentContent("bid.pdf", "h", "", List.of(
                new DocumentContent.PageContent(1, "目标摘录", List.of()), new DocumentContent.PageContent(2, "其他正文", List.of())));
        assertThat(ReportEvidenceLocationPolicy.project(result(2, "目标摘录"), doc, null).evidences().getFirst().pageNumber()).isNull();
    }
    @Test
    void ocrEnrichmentAndBudgetTruncationDoNotInventPhysicalPages() {
        var doc = SidecarPayloadParser.buildDocumentContent("bid.docx", "h", "授权委托书\n目标摘录".repeat(800));
        assertThat(doc.pages()).allSatisfy(p -> assertThat(p.sourcePageNumber()).isNull());
        String context = SemanticPageRetriever.retrieveContext(BidQualityCheckItem.AUTHORIZATION_LETTER,
                TenderRequirements.empty(), doc, 120);
        assertThat(context).contains("内容块", "原件页码未知").doesNotContain("[第 1 页]");
    }
    @Test
    void qualificationAndImageLocationsUseTheSameOriginConstraint() {
        var doc = SidecarPayloadParser.buildDocumentContent("bid.docx", "h", "证书原文");
        var item = result(1, "证书原文").withDetails(List.of(
                new QualificationDetail("证书", "名称", "ATTENTION", 1, "证书原文", null, "人工复核")));
        var projected = ReportEvidenceLocationPolicy.project(item, doc, null);
        assertThat(projected.details().getFirst().bidPageNumber()).isNull();
        var image = QualityCheckItemResult.manualReview(BidQualityCheckItem.SIGNATURE_SEAL, "核对", "核对",
                List.of(new QualityEvidence("BID_IMAGE", "视觉观察", 1, "图像观察", null, 0.9, null, null)));
        assertThat(ReportEvidenceLocationPolicy.project(image, doc, null).evidences().getFirst().pageNumber()).isNull();
    }
    @Test
    void snippetIsAVerbatimSliceOfTheTriggerPage() {
        String raw = "前缀\n真实触发内容\n" + "正文".repeat(200);
        assertThat(raw).contains(BasicInfoCheckPolicy.snippet(raw, 40));
        assertThat(raw).contains(BasicInfoCheckPolicy.snippetAround(raw, 40, "触发"));
    }
}
