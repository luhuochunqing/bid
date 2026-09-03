package com.xiyu.bid.compliance.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 标书质量单个检查项核查结论模型.
 *
 * <p>{@code description} 为检查项说明（PRD 2.3 检查内容），随快照持久化；
 * 历史快照无该字段时由报告读取侧按 itemCode 回填。
 */
public record QualityCheckItemResult(
        String itemCode,
        QualityCheckCategory category,
        String title,
        String description,
        QualityCheckStatus status,
        String severity,
        int order,
        String conclusion,
        String suggestion,
        List<QualityEvidence> evidences,
        List<QualificationDetail> details
) implements Serializable {

    public QualityCheckItemResult {
        if (evidences == null) {
            evidences = Collections.emptyList();
        } else {
            evidences = Collections.unmodifiableList(new ArrayList<>(evidences));
        }
        if (details == null) {
            details = Collections.emptyList();
        } else {
            details = Collections.unmodifiableList(new ArrayList<>(details));
        }
    }

    public QualityCheckItemResult(
            final String itemCode,
            final QualityCheckCategory category,
            final String title,
            final String description,
            final QualityCheckStatus status,
            final String severity,
            final int order,
            final String conclusion,
            final String suggestion,
            final List<QualityEvidence> evidences) {
        this(itemCode, category, title, description, status, severity, order, conclusion, suggestion, evidences, Collections.emptyList());
    }

    public QualityCheckItemResult withDetails(final List<QualificationDetail> newDetails) {
        return new QualityCheckItemResult(
                itemCode, category, title, description, status, severity, order, conclusion, suggestion, evidences, newDetails);
    }

    public static QualityCheckItemResult pass(
            final BidQualityCheckItem item,
            final String conclusion,
            final List<QualityEvidence> evidences) {
        return new QualityCheckItemResult(
                item.getCode(),
                item.getCategory(),
                item.getTitle(),
                item.getDescription(),
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
                item.getDescription(),
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
                item.getDescription(),
                QualityCheckStatus.MANUAL_REVIEW,
                item.getDefaultSeverity(),
                item.getOrder(),
                conclusion,
                suggestion,
                evidences);
    }

    /**
     * 按标准枚举回填 title 与 description（历史快照展示口径与当前标准对齐）.
     * 未知 itemCode 保持原样.
     */
    public QualityCheckItemResult withCanonicalDefinition() {
        for (BidQualityCheckItem item : BidQualityCheckItem.values()) {
            if (item.getCode().equals(itemCode)) {
                return new QualityCheckItemResult(
                        itemCode,
                        category,
                        item.getTitle(),
                        item.getDescription(),
                        status,
                        severity,
                        order,
                        conclusion,
                        suggestion,
                        evidences,
                        details);
            }
        }
        return this;
    }
}
