package com.xiyu.bid.compliance.domain;

/**
 * 标书质量核查大类枚举.
 */
public enum QualityCheckCategory {
    /** 基本信息检查（10项）. */
    BASIC_INFO("基本信息检查", 1),
    /** 分项合规检查（10项）. */
    SUB_ITEM_COMPLIANCE("分项合规检查", 2),
    /** 附件清单检查（5项）. */
    ATTACHMENT_CHECKLIST("附件清单检查", 3);

    private final String label;
    private final int order;

    QualityCheckCategory(final String label, final int order) {
        this.label = label;
        this.order = order;
    }

    public String getLabel() {
        return label;
    }

    public int getOrder() {
        return order;
    }
}
