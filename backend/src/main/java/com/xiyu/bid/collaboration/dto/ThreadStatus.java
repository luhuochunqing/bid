package com.xiyu.bid.collaboration.dto;

/**
 * 讨论线程状态DTO枚举
 * 独立于Entity，用于API层
 */
public enum ThreadStatus {
    OPEN,           // 开放中
    IN_PROGRESS,    // 进行中
    RESOLVED,       // 已解决
    CLOSED          // 已关闭
}
