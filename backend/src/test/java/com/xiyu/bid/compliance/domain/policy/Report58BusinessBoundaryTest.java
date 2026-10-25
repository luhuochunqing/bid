package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class Report58BusinessBoundaryTest {
    @Test
    void slashFinancialRequirementAndReferenceFormatDoNotCreateMandatoryStatements() {
        var requirement = DeterministicFinancialExtractor.extract("（2）财务要求：\n/\n财务报表参考格式");
        assertThat(requirement.required()).isNull();
        assertThat(requirement.requiredStatements()).isEmpty();
    }

    @Test
    void compositeQualificationStillRequiresConfirmation() {
        var result = SemanticEvidenceBoundary.reconcile(QualityCheckItemResult.pass(
                BidQualityCheckItem.QUALIFICATION_REVIEW, "已有资格材料", List.of()), DocumentContent.empty());
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void printedSealInstructionDoesNotProveActualCommitmentSeal() {
        var result = CommitmentSealCheckPolicy.evaluateCommitmentSignature(DocumentContent.fromText("bid.pdf", "hash",
                "承诺函\n本公司郑重承诺具有电商运营能力。特此承诺！\n投标人：某公司（盖章）"));
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }
}
