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
    /** CA certificate will expire within 30 days. */
    CA_EXPIRING,
    /** CA certificate has already expired. */
    CA_EXPIRED,
    /** A CA borrow application is awaiting custodian approval. */
    CA_BORROW_PENDING,
    /** A borrowed CA is approaching its expected return date. */
    CA_BORROW_DUE_SOON,
    /** A borrowed CA is past its expected return date. */
    CA_BORROW_OVERDUE
}
