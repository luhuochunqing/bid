package com.xiyu.bid.approval.enums;

import lombok.Getter;

/**
 * 审批操作类型枚举
 */
@Getter
public enum ApprovalActionType {

    /**
     * 提交审批
     */
    SUBMIT("提交审批"),

    /**
     * 审批通过
     */
    APPROVE("审批通过"),

    /**
     * 审批驳回
     */
    REJECT("审批驳回"),

    /**
     * 取消审批
     */
    CANCEL("取消审批");

    private final String description;

    ApprovalActionType(String pDescription) {
        this.description = pDescription;
    }
}
