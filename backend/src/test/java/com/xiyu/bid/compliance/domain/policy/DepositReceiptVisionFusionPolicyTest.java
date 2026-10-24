package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DepositReceiptVisionFusionPolicyTest {

    @Test
    void realVoucherFacts_promoteToPassWithImageEvidence() {
        var result = DepositReceiptVisionFusionPolicy.fuseDepositReceipt(textPass(),
                DepositReceiptVisionResult.available(List.of(observation(
                        "西域公司", "采购公司", "10000.00", "2026-01-05", "BANK-001", 0.96)), "vision-v1"));

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.evidences()).anySatisfy(evidence -> {
            assertThat(evidence.sourceType()).isEqualTo("BID_IMAGE");
            assertThat(evidence.pageNumber()).isEqualTo(12);
            assertThat(evidence.quote()).contains("10000.00").contains("BANK-001");
        });
    }

    @Test
    void textPassWithUnavailableVision_doesNotStayPass() {
        var result = DepositReceiptVisionFusionPolicy.fuseDepositReceipt(textPass(),
                DepositReceiptVisionResult.unavailable("未抽取到 PDF 页面图像"));

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("文本层不能单独证明");
    }

    @Test
    void sparseFacts_cannotInventVoucherPass() {
        var result = DepositReceiptVisionFusionPolicy.fuseDepositReceipt(textPass(),
                DepositReceiptVisionResult.available(List.of(observation(
                        "", "", "10000.00", "", "", 0.95)), "vision-v1"));

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("真实交易要素");
    }

    private static QualityCheckItemResult textPass() {
        return QualityCheckItemResult.pass(BidQualityCheckItem.DEPOSIT_RECEIPT,
                "文本出现保证金回单", List.of());
    }

    private static DepositReceiptVisionResult.Observation observation(
            final String payer, final String payee, final String amount,
            final String date, final String serial, final double confidence) {
        return new DepositReceiptVisionResult.Observation(12, true, confidence,
                payer, payee, amount, date, serial, 0.1, 0.2, 0.8, 0.4);
    }
}
