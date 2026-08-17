package com.xiyu.bid.scoreparse.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/** 自动路径熔断：窗口内 AUTO 失败达到阈值则挡住新的 AUTO；MANUAL COMPLETED 立即解除。 */
public final class AutoFailCircuit {

    public static final int THRESHOLD = 2;
    public static final Duration WINDOW = Duration.ofMinutes(30);
    public static final String OPEN_MESSAGE = "自动路径已停，请检查文件后手点重新解析或重新打分";

    private AutoFailCircuit() {
    }

    public static boolean isOpen(int autoFailedInWindow) {
        return isOpen(autoFailedInWindow, null, null);
    }

    public static boolean isOpen(int autoFailedInWindow, LocalDateTime latestAutoFail,
                                 LocalDateTime latestManualCompleted) {
        if (autoFailedInWindow < THRESHOLD) {
            return false;
        }
        return latestManualCompleted == null || latestAutoFail == null
                || latestManualCompleted.isBefore(latestAutoFail);
    }

    public static boolean inWindow(LocalDateTime completedAt, LocalDateTime now) {
        return completedAt != null && !completedAt.isBefore(now.minus(WINDOW));
    }
}
