package com.xiyu.bid.approval.core;

/**
 * 审批规则判定结果
 */
public record ApprovalRuleResult(boolean allowed, String reason) {

    public static ApprovalRuleResult allow() {
        return new ApprovalRuleResult(true, null);
    }

    public static ApprovalRuleResult deny(String reason) {
        return new ApprovalRuleResult(false, reason);
    }
}
