// Input: 明确证书主体、无主语续句及明确其他对象的最小范围样本
// Output: 入口、最终边界、真实领域引擎共享保守复核结果
// Pos: compliance/domain/policy - 用户批准的跨句能力收口契约
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CertificateEvidenceScopeContractTest {
    @Test
    void explicitCertificateFactsKeepPositiveAndNegativeDecisions() {
        assertAcrossEntrypoints("本公司具有ISO9001认证。", false, QualityCheckStatus.PASS);
        assertAcrossEntrypoints("本公司具有ISO9001认证。ISO9001认证已过期。", false,
                QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void unattributedSamePageStatusRequiresReview() {
        assertAcrossEntrypoints("本公司具有ISO9001认证。未到场。", false, QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void unattributedNextPageStatusRequiresReview() {
        assertAcrossEntrypoints("本公司具有ISO9001认证。\n\f\n未到场。", true,
                QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void explicitlyDifferentObjectDoesNotChangeCertificateFact() {
        assertAcrossEntrypoints("本公司具有ISO9001认证。项目经理未到场。", false, QualityCheckStatus.PASS);
    }

    private static void assertAcrossEntrypoints(String text, boolean splitPages, QualityCheckStatus expected) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var requirements = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String[] parts = text.split("\n\f\n");
        var bid = splitPages
                ? new DocumentContent("bid", "hash", text, List.of(
                        new DocumentContent.PageContent(1, parts[0], List.of()),
                        new DocumentContent.PageContent(2, parts[1], List.of())), List.of())
                : DocumentContent.fromText("bid", "hash", text);
        var entry = QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), requirements,
                List.of(evidence)).orElseThrow();
        assertThat(entry.status()).as("qualification entry").isEqualTo(expected);
        var proposed = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
        var boundary = SemanticEvidenceBoundary.reconcile(proposed, bid);
        assertThat(boundary.status()).as("final boundary").isEqualTo(expected);
        assertThat(boundary.evidences()).contains(evidence);
        var engine = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                requirements, Set.of()).stream()
                .filter(result -> result.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst().orElseThrow();
        assertThat(engine.status()).as("domain engine").isEqualTo(expected);
    }
}
