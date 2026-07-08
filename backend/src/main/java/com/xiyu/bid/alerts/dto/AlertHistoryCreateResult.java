package com.xiyu.bid.alerts.dto;

import com.xiyu.bid.alerts.entity.AlertHistory;

/**
 * 告警历史创建结果值对象：明确区分"新建"与"复用已有未处理记录"。
 *
 * <p>由 {@link com.xiyu.bid.alerts.service.AlertHistoryService#createAlertHistoryIfAbsent}
 * 返回，供调用方决定是否触发后续通知——仅在 {@link #created()} 为 {@code true} 时才推送通知，
 * 避免对同一未处理告警重复推送。</p>
 *
 * <p>本类为不可变 record，符合 FP-Java Profile 中"DTO/值对象优先用 record"的约定。</p>
 *
 * @param alertHistory 告警历史记录（新建已保存，或复用的已有未处理记录）
 * @param created      是否为本次新建：{@code true}=新建并已持久化；{@code false}=复用已有未处理记录
 */
public record AlertHistoryCreateResult(
        AlertHistory alertHistory,
        boolean created
) {
}
