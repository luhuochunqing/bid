package com.xiyu.bid.approval.enums;

import lombok.Getter;

/**
 * 审批状态枚举
 */
@Getter
public enum ApprovalStatus {

    /**
     * 待审批
     */
    PENDING("待审批"),

    /**
     * 已通过
     */
    APPROVED("已通过"),

    /**
     * 已驳回
     */
    REJECTED("已驳回"),

    /**
     * 已取消
     */
    CANCELLED("已取消");

    private final String description;

    ApprovalStatus(String pDescription) {
        this.description = pDescription;
    }

    /**
     * 检查是否为最终状态
     */
    public boolean isFinalStatus() {
        return this == APPROVED || this == REJECTED || this == CANCELLED;
    }

    /**
     * 检查是否可以转换到目标状态
     */
    public boolean canTransitionTo(ApprovalStatus target) {
        return switch (this) {
            case PENDING -> target == APPROVED || target == REJECTED || target == CANCELLED;
            case APPROVED, REJECTED, CANCELLED -> false;
        };
    }
}
