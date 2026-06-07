// Input: enum constant requested by callers
// Output: reason a delivery was skipped
// Pos: Pure Core/跳过原因枚举
package com.xiyu.bid.notification.outbound.core;

public enum SkipReason {
    NOT_BOUND,
    DISABLED,
    ERROR
}
