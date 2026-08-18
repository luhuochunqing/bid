package com.xiyu.bid.compliance.domain;

/**
 * 标书质量单项核查结论状态.
 */
public enum QualityCheckStatus {
    /** 检查通过. */
    PASS("通过"),
    /** 检查不通过（存在明确合规/实质性缺陷）. */
    FAIL("不通过"),
    /** 待人工确认（需要人工结合非结构化内容复核）. */
    MANUAL_REVIEW("待确认");

    private final String label;

    QualityCheckStatus(final String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
