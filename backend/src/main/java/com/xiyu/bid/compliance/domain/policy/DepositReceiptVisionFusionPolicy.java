package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.List;

/** 保证金文本结论与页面视觉事实的保守融合策略。 */
public final class DepositReceiptVisionFusionPolicy {
    private static final double MIN_CONFIDENCE = 0.80;

    private DepositReceiptVisionFusionPolicy() {
    }

    public static QualityCheckItemResult fuseDepositReceipt(
            final QualityCheckItemResult textResult,
            final DepositReceiptVisionResult vision) {
        if (vision == null || !vision.available()) {
            return manual("保证金凭证视觉检测不可用，文本层不能单独证明已附真实回单",
                    vision == null ? "结果缺失" : vision.unavailableReason(), textResult.evidences());
        }
        List<QualityEvidence> evidences = new ArrayList<>(textResult.evidences());
        List<DepositReceiptVisionResult.Observation> valid = vision.observations().stream()
                .filter(DepositReceiptVisionFusionPolicy::isValidVoucher).toList();
        valid.stream().map(DepositReceiptVisionFusionPolicy::toEvidence).forEach(evidences::add);
        if (valid.isEmpty()) {
            return manual("页面视觉未提取到具备真实交易要素的保证金凭证",
                    "未识别到付款人、收款人、金额、日期或银行流水号", evidences);
        }
        return QualityCheckItemResult.pass(BidQualityCheckItem.DEPOSIT_RECEIPT,
                "页面视觉识别到保证金真实凭证及交易要素", evidences);
    }

    private static boolean isValidVoucher(final DepositReceiptVisionResult.Observation item) {
        if (!item.voucherDetected() || item.confidence() < MIN_CONFIDENCE) {
            return false;
        }
        int populated = count(item.payer(), item.payee(), item.amount(), item.transactionDate(), item.bankSerialNumber());
        return populated >= 3 && (!item.amount().isBlank() || !item.bankSerialNumber().isBlank());
    }

    private static int count(final String... values) {
        int result = 0;
        for (String value : values) {
            if (!value.isBlank()) {
                result++;
            }
        }
        return result;
    }

    private static QualityEvidence toEvidence(final DepositReceiptVisionResult.Observation item) {
        String quote = "AI视觉识别保证金凭证 payer=" + item.payer() + " payee=" + item.payee()
                + " amount=" + item.amount() + " date=" + item.transactionDate()
                + " serial=" + item.bankSerialNumber() + " confidence=" + item.confidence();
        return new QualityEvidence("BID_IMAGE", "保证金凭证视觉识别", item.pageNumber(), quote,
                null, item.confidence(), null, null);
    }

    private static QualityCheckItemResult manual(
            final String conclusion, final String reason, final List<QualityEvidence> evidences) {
        return QualityCheckItemResult.manualReview(BidQualityCheckItem.DEPOSIT_RECEIPT,
                conclusion, "请人工复核保证金凭证；视觉检测原因：" + reason, evidences);
    }
}
