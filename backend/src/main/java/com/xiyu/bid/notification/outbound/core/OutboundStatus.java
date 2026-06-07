// Input: enum constant requested by callers
// Output: outbound delivery status
// Pos: Pure Core/出站状态枚举
package com.xiyu.bid.notification.outbound.core;

public enum OutboundStatus {
    SENT,
    FAILED,
    SKIPPED
}
