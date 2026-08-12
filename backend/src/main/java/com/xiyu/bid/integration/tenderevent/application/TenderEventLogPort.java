package com.xiyu.bid.integration.tenderevent.application;

/**
 * 标讯事件流水记录端口（六边形架构的出站端口）。
 *
 * <p>由基础设施层实现（{@code TenderEventLogWriter}），持久化每次推送结果，用于问题定位。
 */
public interface TenderEventLogPort {

    /**
     * 记录一次标讯事件推送结果。
     *
     * @param log 推送流水记录
     */
    void record(TenderEventLogRecord log);
}