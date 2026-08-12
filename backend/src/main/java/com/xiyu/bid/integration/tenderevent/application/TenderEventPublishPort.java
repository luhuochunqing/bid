package com.xiyu.bid.integration.tenderevent.application;

/**
 * 标讯事件发送端口（六边形架构的出站端口）。
 *
 * <p>由基础设施层实现（{@code TenderEventSdkProducer}），负责把事件命令投递到西域事件总线。
 * 返回 {@code true} 表示发送成功、{@code false} 表示发送失败（供流水记录失败原因）。
 */
public interface TenderEventPublishPort {

    /**
     * 发送标讯事件。
     *
     * @param command 事件发布命令（含事件编码、消息体、链路追踪信息）
     * @return 发送是否成功
     */
    boolean publish(TenderEventPublishCommand command);
}