package com.xiyu.bid.compliance.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 标书质量单个检查项核查结论模型.
 */
public record QualityCheckItemResult(
        String itemCode,
        QualityCheckCategory category,
        String title,
        QualityCheckStatus status,
        String severity,
        int order,
        String conclusion,
        String suggestion,
        List<QualityEvidence> evidences
) implements Serializable {

    public QualityCheckItemResult {
        if (evidences == null) {
            evidences = Collections.emptyList();
        } else {
            evidences = Collections.unmodifiableList(new ArrayList<>(evidences));
        }
    }

    public static QualityCheckItemResult pass(
            final BidQualityCheckItem item,
            final String conclusion,
            final List<QualityEvidence> evidences) {
        return new QualityCheckItemResult(
                item.getCode(),
                item.getCategory(),
                item.getTitle(),
                QualityCheckStatus.PASS,
                item.getDefaultSeverity(),
                item.getOrder(),
                conclusion,
                "",
                evidences);
    }

    public static QualityCheckItemResult fail(
            final BidQualityCheckItem item,
            final String conclusion,
            final String suggestion,
            final List<QualityEvidence> evidences) {
        return new QualityCheckItemResult(
                item.getCode(),
                item.getCategory(),
                item.getTitle(),
                QualityCheckStatus.FAIL,
                item.getDefaultSeverity(),
                item.getOrder(),
                conclusion,
                suggestion,
                evidences);
    }

    public static QualityCheckItemResult manualReview(
            final BidQualityCheckItem item,
            final String conclusion,
            final String suggestion,
            final List<QualityEvidence> evidences) {
        return new QualityCheckItemResult(
                item.getCode(),
                item.getCategory(),
                item.getTitle(),
                QualityCheckStatus.MANUAL_REVIEW,
                item.getDefaultSeverity(),
                item.getOrder(),
                conclusion,
                suggestion,
                evidences);
    }
}
