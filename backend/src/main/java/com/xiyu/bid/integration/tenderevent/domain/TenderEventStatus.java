package com.xiyu.bid.integration.tenderevent.domain;

/** 标讯事件推送流水状态（纯核心）。 */
public enum TenderEventStatus {
    /** 已成功发送。 */
    SENT,
    /** 发送失败（记录失败原因，用于问题定位）。 */
    FAILED
}