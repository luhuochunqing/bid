// Input: notification category constant requested by callers
// Output: enum value indicating notification kind
// Pos: Pure Core/通知类型枚举
package com.xiyu.bid.notification.core;

public enum NotificationType {
    INFO,
    SYSTEM,
    MENTION,
    APPROVAL,
    DEADLINE,
    TASK_UPDATE,
    DOCUMENT_CHANGE,
    TENDER_MATCH,
    BID_REVIEW,
    /** 待立项通知 */
    PENDING_INITIATION,
    /** 待结项申请通知 */
    PENDING_CLOSURE_APPLICATION,
    /** CA certificate will expire within 30 days. */
    CA_EXPIRING,
    /** CA certificate has already expired. */
    CA_EXPIRED,
    /** A CA borrow application is awaiting custodian approval. */
    CA_BORROW_PENDING,
    /** A borrowed CA is approaching its expected return date. */
    CA_BORROW_DUE_SOON,
    /** A borrowed CA is past its expected return date. */
    CA_BORROW_OVERDUE,
    /** A CA borrow application has been approved by custodian. */
    CA_BORROW_APPROVED,
    /** 商机预测提醒（预测下次招标时间已到，提示创建商机） */
    OPPORTUNITY_PREDICTION
}
