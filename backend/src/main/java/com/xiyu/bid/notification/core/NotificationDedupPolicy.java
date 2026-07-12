// Input: current instant + list of existing notification timestamps
// Output: true if a notification was already created within the dedup window
// Pos: Pure Core/通知去重策略
package com.xiyu.bid.notification.core;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 通知去重策略 —— 纯核心。
 *
 * <p>不读写数据库、不访问系统时间。调用方负责查询已有通知时间戳并显式传入当前时间。</p>
 */
public final class NotificationDedupPolicy {

    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);

    private NotificationDedupPolicy() {
    }

    /**
     * 判断给定业务动作是否已在默认 5 分钟窗口内发送过通知。
     *
     * @param now                当前时间（显式传入，避免隐式系统依赖）
     * @param existingTimestamps 同一业务动作已创建通知的时间戳列表
     * @return true 表示窗口内已存在通知，应跳过；false 表示允许创建新通知
     */
    public static boolean isDuplicate(Instant now, List<Instant> existingTimestamps) {
        return isDuplicate(now, existingTimestamps, DEFAULT_WINDOW);
    }

    /**
     * 判断给定业务动作是否已在指定时间窗口内发送过通知。
     *
     * @param now                当前时间
     * @param existingTimestamps 已有通知时间戳列表
     * @param window             去重窗口
     * @return true 表示窗口内已存在通知，应跳过；false 表示允许创建新通知
     */
    public static boolean isDuplicate(Instant now, List<Instant> existingTimestamps, Duration window) {
        if (now == null || existingTimestamps == null || existingTimestamps.isEmpty()) {
            return false;
        }
        Duration effectiveWindow = window == null ? DEFAULT_WINDOW : window;
        Instant windowStart = now.minus(effectiveWindow);
        return existingTimestamps.stream()
            .anyMatch(ts -> ts != null && !ts.isBefore(windowStart) && !ts.isAfter(now));
    }
}
