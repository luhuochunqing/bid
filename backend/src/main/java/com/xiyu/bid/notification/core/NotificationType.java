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
    BID_REVIEW
}
