package com.xiyu.bid.entity;

/**
 * 标讯状态枚举
 */
public enum TenderStatus {
    PENDING_ASSIGNMENT, // 待分配
    TRACKING,           // 跟踪中
    EVALUATED,          // 已评估
    BIDDING,            // 投标中
    WON,                // 已中标
    LOST,               // 未中标
    ABANDONED           // 已放弃
}
