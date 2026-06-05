package com.xiyu.bid.project.core;

/**
 * 立项审核状态。产品蓝图 V1.1 §4.3。
 */
public enum InitiationReviewStatus {
    DRAFT,           // 草稿（未提交）
    PENDING_REVIEW,  // 待审核
    APPROVED,        // 已通过
    REJECTED         // 已驳回
}
